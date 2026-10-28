"""Meta Lake Git mirror + Java structural extractor + PyIceberg writer (single POC worker).

Git repositories are HTTPS-only, host-allowlisted, ref-pinned to a commit and mirrored
under an immutable source/commit prefix before any Iceberg records are committed.
"""
from __future__ import annotations
import base64
import secrets
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
from typing import Any
from urllib.parse import urlsplit, quote

from fastapi import FastAPI, Header, HTTPException
from pydantic import BaseModel, Field

app = FastAPI(title="Meta Lake Git Worker", version="0.1.0")
IDENTIFIER = re.compile(r"^[A-Za-z_][A-Za-z0-9_]*$")
REF = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._/-]{0,199}$")

class Branches(BaseModel):
    repositoryUrl: str
    credentialRef: str | None = None

class Normalize(BaseModel):
    sourceId: int = Field(gt=0)
    jobId: int = Field(gt=0)
    repositoryUrl: str
    branch: str
    credentialRef: str | None = None


def require_token(token: str | None) -> None:
    expected = os.environ.get("METALAKE_GIT_SERVICE_TOKEN", "")
    if not expected:
        raise HTTPException(503, "Git service token is not configured")
    if not token or not secrets.compare_digest(token, expected):
        raise HTTPException(401, "Unauthorized")


def validate_repository(url: str, branch: str | None = None) -> str:
    u = urlsplit(url)
    allow = {x.strip().lower() for x in os.environ.get("GIT_ALLOWED_HOSTS", "github.com,gitlab.com").split(",") if x.strip()}
    if u.scheme != "https" or not u.hostname or u.hostname.lower() not in allow or u.username or u.password or u.query or u.fragment or not u.path.startswith("/") or ".." in u.path.split("/"):
        raise ValueError("Git repository must be an allowlisted HTTPS URL")
    if branch is not None and (not REF.fullmatch(branch) or branch.startswith("-") or ".." in branch or "//" in branch or branch.endswith(".lock")):
        raise ValueError("Invalid Git branch")
    return url


def git_env(credential_ref: str | None) -> dict[str, str]:
    env = os.environ.copy()
    env.update({"GIT_TERMINAL_PROMPT": "0", "GIT_CONFIG_NOSYSTEM": "1", "GIT_ALLOW_PROTOCOL": "https"})
    if credential_ref:
        if not re.fullmatch(r"[A-Za-z0-9_\-]{1,80}", credential_ref):
            raise ValueError("Invalid credentialRef")
        name = "GIT_TOKEN_" + credential_ref.upper().replace("-", "_")
        token = os.environ.get(name)
        if not token:
            raise ValueError("Credential reference is not configured in the Git worker")
        username = os.environ.get("GIT_HTTP_USERNAME", "oauth2")
        encoded = base64.b64encode(f"{username}:{token}".encode()).decode()
        env["GIT_CONFIG_COUNT"] = "1"
        env["GIT_CONFIG_KEY_0"] = "http.extraheader"
        env["GIT_CONFIG_VALUE_0"] = "Authorization: Basic " + encoded
    return env


def git(*args: str, cwd: Path | None = None, env: dict | None = None) -> str:
    try:
        result = subprocess.run(["git", "-c", "credential.helper=", *args], cwd=cwd, env=env, capture_output=True, timeout=180, check=True)
        return result.stdout.decode("utf-8", errors="replace").strip()
    except (subprocess.CalledProcessError, subprocess.TimeoutExpired) as e:
        # subprocess stderr might include secrets / internal URL; do not expose it
        raise RuntimeError("Git command failed or timed out") from e


def list_branches(url: str, credential_ref: str | None = None) -> list[str]:
    validate_repository(url)
    out = git("ls-remote", "--heads", url, env=git_env(credential_ref))
    return sorted({line.split("refs/heads/", 1)[1] for line in out.splitlines() if "refs/heads/" in line})


def tracked_files(root: Path) -> list[Path]:
    raw = subprocess.run(["git", "ls-files", "-z"], cwd=root, capture_output=True, check=True).stdout
    files = []
    for entry in raw.split(b"\0"):
        if not entry:
            continue
        name = os.fsdecode(entry)
        p = root / name
        if not p.is_file() or p.is_symlink():
            continue
        if p.stat().st_size > int(os.environ.get("GIT_MAX_FILE_BYTES", "5000000")):
            raise ValueError("Git repository includes a file above the configured size limit")
        files.append(p)
    max_files = int(os.environ.get("GIT_MAX_FILES", "3000"))
    if len(files) > max_files:
        raise ValueError("Git repository file count exceeds configured limit")
    return files


def read_node(node, raw: bytes) -> str:
    return raw[node.start_byte:node.end_byte].decode("utf-8", errors="replace") if node else ""


def java_facts(content: str, relative_path: str, source_id: int, job_id: int, commit: str) -> tuple[list[dict], list[dict]]:
    """tree-sitter Java syntax: only directly observed structure, unresolved calls stay unresolved."""
    import tree_sitter_java
    from tree_sitter import Language, Parser
    parser = Parser(Language(tree_sitter_java.language()))
    raw = content.encode("utf-8")
    tree = parser.parse(raw)
    symbols: list[dict] = []
    relations: list[dict] = []
    declarations = {
        "class_declaration": "CLASS", "interface_declaration": "INTERFACE",
        "enum_declaration": "ENUM", "record_declaration": "RECORD",
        "method_declaration": "METHOD", "constructor_declaration": "CONSTRUCTOR",
    }
    def walk(node, parents: list[str], parent_id: str | None) -> None:
        kind = declarations.get(node.type)
        next_parents, next_id = parents, parent_id
        if kind:
            name = read_node(node.child_by_field_name("name"), raw)
            if name:
                qualified = ".".join([*parents, name])
                symbol_id = hashlib.sha256(f"{relative_path}:{qualified}:{node.start_point.row + 1}:{kind}".encode()).hexdigest()[:24]
                symbols.append(dict(
                    processing_job_id=job_id, source_id=source_id, relative_path=relative_path,
                    symbol_id=symbol_id, parent_symbol_id=parent_id, symbol_type=kind,
                    qualified_name=qualified, signature=read_node(node.child_by_field_name("parameters"), raw),
                    start_line=node.start_point.row + 1, end_line=node.end_point.row + 1,
                    content=read_node(node, raw)))
                if parent_id:
                    relations.append(dict(processing_job_id=job_id, source_id=source_id,
                        source_symbol_id=parent_id, target_symbol_id=symbol_id,
                        relation_type="CONTAINS", resolution_status="RESOLVED"))
                if kind in ("CLASS", "INTERFACE", "ENUM", "RECORD"):
                    next_parents, next_id = [*parents, name], symbol_id
                    for child in node.named_children:
                        if child.type in ("superclass", "super_interfaces", "extends_interfaces"):
                            relations.append(dict(processing_job_id=job_id, source_id=source_id,
                                source_symbol_id=symbol_id, target_symbol_id=read_node(child, raw),
                                relation_type="EXTENDS" if child.type != "super_interfaces" else "IMPLEMENTS",
                                resolution_status="UNRESOLVED"))
                else:
                    next_id = symbol_id
        if node.type == "method_invocation" and parent_id:
            called = read_node(node.child_by_field_name("name"), raw)
            if called:
                relations.append(dict(processing_job_id=job_id, source_id=source_id,
                    source_symbol_id=parent_id, target_symbol_id=f"unresolved:{called}",
                    relation_type="CALLS", resolution_status="UNRESOLVED"))
        for child in node.named_children:
            walk(child, next_parents, next_id)
    walk(tree.root_node, [], None)
    return symbols, relations


def platform_s3():
    import boto3
    from botocore.config import Config
    return boto3.client("s3", endpoint_url=os.environ["PLATFORM_S3_ENDPOINT"],
        aws_access_key_id=os.environ["PLATFORM_S3_ACCESS_KEY"],
        aws_secret_access_key=os.environ["PLATFORM_S3_SECRET_KEY"],
        region_name=os.getenv("PLATFORM_S3_REGION", "us-east-1"),
        config=Config(s3={"addressing_style": "path"}))


def mirror_location(source_id: int, job_id: int, commit: str) -> str:
    bucket = os.environ["GIT_MIRROR_BUCKET"]
    prefix = os.environ.get("GIT_MIRROR_PREFIX", "git").strip("/")
    # Job-specific MANAGED paths avoid two filesets deleting each other's data.
    target = f"{prefix}/source-{source_id}/commits/{commit}/job-{job_id}"
    return f"s3a://{bucket}/{target}/"


def mirror_files(uri: str, root: Path, paths: list[Path]) -> None:
    """Mirror a pinned checkout after its MANAGED Fileset location is created."""
    parsed = urlsplit(uri)
    bucket, prefix = parsed.netloc, parsed.path.lstrip("/")
    client = platform_s3()
    for file in paths:
        rel = file.relative_to(root).as_posix()
        client.upload_file(str(file), bucket, f"{prefix}{rel}")


def read_mirrored_file(client, uri: str, relative_path: str) -> bytes:
    """Normalize bytes from platform mirror, not the original mutable Git checkout."""
    parsed = urlsplit(uri)
    if parsed.scheme != "s3a" or not parsed.netloc:
        raise ValueError("Unexpected mirror URI")
    key = parsed.path.lstrip("/") + relative_path
    response = client.get_object(Bucket=parsed.netloc, Key=key)
    return response["Body"].read()


def register_fileset(uri: str, source_id: int, job_id: int) -> dict[str, str] | None:
    """Optional MANAGED Fileset registration in preprovisioned Git catalog/schema.
    Catalog/schema creation and its Credentials Vending settings are infrastructure tasks.
    """
    catalog = os.getenv("GRAVITINO_GIT_CATALOG")
    schema = os.getenv("GRAVITINO_GIT_SCHEMA")
    if not catalog or not schema:
        raise ValueError("GRAVITINO_GIT_CATALOG and GRAVITINO_GIT_SCHEMA are required for Git mirror registration")
    import httpx
    metalake = os.getenv("GRAVITINO_METALAKE", "ax")
    name = f"git_source_{source_id}_job_{job_id}"
    api = (os.environ["GRAVITINO_URL"].rstrip("/") + "/api/metalakes/" + quote(metalake) +
           "/catalogs/" + quote(catalog) + "/schemas/" + quote(schema) + "/filesets")
    headers = {"Accept": "application/vnd.gravitino.v1+json"}
    if os.getenv("GRAVITINO_TOKEN"):
        headers["Authorization"] = "Bearer " + os.environ["GRAVITINO_TOKEN"]
    with httpx.Client(timeout=60) as client:
        response = client.post(api, headers=headers, json={"name": name, "type": "MANAGED",
            "storageLocation": uri, "properties": {"source-id": str(source_id), "job-id": str(job_id)}})
        response.raise_for_status()
    return {"metalake": metalake, "catalog": catalog, "schema": schema, "name": name}


TABLE_COLUMNS = {
    "code_files": [
        ("processing_job_id", "long"), ("source_id", "long"), ("relative_path", "string"),
        ("commit_sha", "string"), ("language", "string"), ("package_name", "string"),
        ("content", "string"), ("checksum_sha256", "string")],
    "code_symbols": [
        ("processing_job_id", "long"), ("source_id", "long"), ("relative_path", "string"),
        ("symbol_id", "string"), ("parent_symbol_id", "string"), ("symbol_type", "string"),
        ("qualified_name", "string"), ("signature", "string"), ("start_line", "int"),
        ("end_line", "int"), ("content", "string")],
    "code_relations": [
        ("processing_job_id", "long"), ("source_id", "long"), ("source_symbol_id", "string"),
        ("target_symbol_id", "string"), ("relation_type", "string"), ("resolution_status", "string")]
}


def write_iceberg(tables: dict[str, list[dict]]) -> list[dict[str, Any]]:
    import pyarrow as pa
    from pyiceberg.catalog import load_catalog
    from pyiceberg.schema import Schema
    from pyiceberg.types import NestedField, LongType, IntegerType, StringType
    types = {"long": (LongType, pa.int64), "int": (IntegerType, pa.int32), "string": (StringType, pa.string)}
    config = {
        "uri": os.environ["ICEBERG_REST_URI"],
        # Gravitino IRC provides table-scoped FileIO credentials on load/create.
        "header.X-Iceberg-Access-Delegation": "vended-credentials",
        "s3.endpoint": os.environ["PLATFORM_S3_ENDPOINT"],
        "s3.region": os.getenv("PLATFORM_S3_REGION", "us-east-1"),
        "s3.path-style-access": "true"
    }
    catalog = load_catalog("git_iceberg", **config)
    ns = os.getenv("ICEBERG_NAMESPACE", "ai_ready_poc")
    catalog.create_namespace_if_not_exists(ns)
    result: list[dict[str, Any]] = []
    for name, cols in TABLE_COLUMNS.items():
        ident = (ns, name)
        try:
            table = catalog.load_table(ident)
        except Exception as ex:
            from pyiceberg.exceptions import NoSuchTableError
            if not isinstance(ex, NoSuchTableError):
                raise
            fields = [NestedField(i, col, types[typ][0](), required=False) for i, (col, typ) in enumerate(cols, start=1)]
            table = catalog.create_table(ident, schema=Schema(*fields))
        rows = tables[name]
        if rows:
            # Use the table's actual schema to avoid silently writing wrong columns
            actual = {field.name for field in table.schema().fields}
            needed = {col for col, _ in cols}
            if not needed.issubset(actual):
                raise ValueError(f"Iceberg table {name} has an incompatible schema")
            arrow_schema = pa.schema([pa.field(col, types[typ][1](), nullable=True) for col, typ in cols])
            table.append(pa.Table.from_pylist(rows, schema=arrow_schema))
            table = catalog.load_table(ident)
        snapshot = table.current_snapshot() if rows else None
        result.append({"kind": name.upper(), "catalog": os.getenv("ICEBERG_CATALOG_LABEL", "ai-ready-catalog"),
            "namespace": ns, "table": name, "recordCount": len(rows),
            "snapshotId": snapshot.snapshot_id if snapshot else None, "tableLocation": table.location()})
    return result


@app.get("/health")
def health():
    return {"status": "UP"}

@app.post("/internal/git/branches")
def branches(req: Branches, x_internal_token: str | None = Header(default=None)):
    require_token(x_internal_token)
    try:
        return {"branches": list_branches(req.repositoryUrl, req.credentialRef)}
    except ValueError as ex:
        raise HTTPException(422, str(ex)) from ex
    except Exception as ex:
        raise HTTPException(502, type(ex).__name__) from ex

@app.post("/internal/git/normalize")
def normalize(req: Normalize, x_internal_token: str | None = Header(default=None)):
    require_token(x_internal_token)
    try:
        url = validate_repository(req.repositoryUrl, req.branch)
        with tempfile.TemporaryDirectory(prefix="metalake-git-") as tmp:
            root = Path(tmp) / "repository"
            git("clone", "--depth", "1", "--single-branch", "--branch", req.branch, "--", url, str(root), env=git_env(req.credentialRef))
            commit = git("rev-parse", "HEAD", cwd=root)
            files = tracked_files(root)
            if not files:
                raise ValueError("No files in Git branch")
            # Provision MANAGED Fileset before uploading any bytes, so Gravitino owns the path.
            mirror_uri = mirror_location(req.sourceId, req.jobId, commit)
            fileset = register_fileset(mirror_uri, req.sourceId, req.jobId)
            # Mirror BEFORE Iceberg writes so every output can be traced back to the exact input.
            mirror_files(mirror_uri, root, files)
            tables = {name: [] for name in TABLE_COLUMNS}
            mirror_client = platform_s3()
            for path in files:
                relative = path.relative_to(root).as_posix()
                raw = read_mirrored_file(mirror_client, mirror_uri, relative)
                if b"\0" in raw[:8192]:
                    continue
                try:
                    content = raw.decode("utf-8")
                except UnicodeDecodeError:
                    continue
                language = "JAVA" if path.suffix.lower() == ".java" else "TEXT"
                tables["code_files"].append(dict(processing_job_id=req.jobId, source_id=req.sourceId,
                    relative_path=relative, commit_sha=commit, language=language,
                    package_name=None, content=content, checksum_sha256=hashlib.sha256(raw).hexdigest()))
                if language == "JAVA":
                    symbols, relations = java_facts(content, relative, req.sourceId, req.jobId, commit)
                    tables["code_symbols"].extend(symbols)
                    tables["code_relations"].extend(relations)
            if not tables["code_files"]:
                raise ValueError("No UTF-8 source files to normalize")
            outputs = write_iceberg(tables)
            return {"sourceRevision": commit, "mirrorUri": mirror_uri, "fileset": fileset,
                    "outputs": outputs, "processedFiles": len(tables["code_files"]), "mirroredFiles": len(files),
                    "processedPaths": [row["relative_path"] for row in tables["code_files"]]}
    except ValueError as ex:
        raise HTTPException(422, str(ex)) from ex
    except Exception as ex:
        raise HTTPException(502, type(ex).__name__) from ex
