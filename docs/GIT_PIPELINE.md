# Meta Lake Git pipeline (Git-only add-on to Vector v2 + Lance Catalog)

## Scope and safety

This add-on modifies **only three existing Java classes** via context-checked `git apply` and installs four Git-specific Java classes plus one Python Git worker. **This edition configures PyIceberg to request vended storage credentials from Gravitino IRC** rather than injecting platform S3 keys into Iceberg catalog configuration. The platform S3 keys remain necessary for direct boto3 Git mirror upload/read. It preserves the existing Docling, Iceberg DOCUMENTS, BGE-M3, Lance and Gravitino Lance Catalog code. No Git model embedding in this phase.

**Validation limits:** a synthetic Java patch application test and Python unit/syntax checks have been run; the actual Windows project Gradle compilation and your private MinIO/Gravitino/Iceberg E2E need to be executed locally. A remote repository/Windows commit is not created by this package.

## 0. Commit the known-good version first

From Git Bash `cd /c/Datastreams/meta-lake-worker` run `git status --short`, `git diff --check`, and commit the current tested PDF/Vector/Lance changes using your usual Git workflow. Exclude `.env`, `.metalake-*-backup-*`, `.venv`, secrets, and temporary files. Make a dedicated second commit for Git.

## 1. Install Java (Windows, Git Bash; no Python required)

```bash
cd /c/Datastreams/meta-lake-git-v1-addon
bash apply_git_only.sh /c/Datastreams/meta-lake-worker
cd /c/Datastreams/meta-lake-worker
./gradlew compileJava
git diff --check
git status --short
```

**If patch checking fails, stop.** The installed Java files do not exactly match the known Vector v2 + Lance patch; don't overwrite them with the old v3 overlay. Review a targeted rebase instead.

Spring Boot `application.yml`:

```yaml
metalake:
  git:
    service-url: ${METALAKE_GIT_SERVICE_URL:http://10.194.1.140:8110}
    service-token: ${METALAKE_GIT_SERVICE_TOKEN:}
```

Pass `METALAKE_GIT_SERVICE_TOKEN` to Spring Boot; it must match the Linux Git worker. Restart Spring Boot after the Java changes.

## 2. Prepare a platform-owned Gravitino Git Fileset catalog

The worker expects `ax.git_managed.mirrors` to exist; this is an S3/MinIO **Fileset** catalog pointing to the **platform-owned** `10.194.1.140:9000` MinIO, not an external customer's bucket.

Check first:

```bash
curl -fsS -H 'Accept: application/vnd.gravitino.v1+json' \
  http://10.194.1.140:8090/api/metalakes/ax/catalogs/git_managed | jq '{catalog: .catalog.name, type: .catalog.type, location: .catalog.properties.location}'
```

If absent, create a Gravitino `FILESET` catalog with `location=s3a://data-platform/metalake/git`, `s3-endpoint=http://10.194.1.140:9000`, and platform MinIO `s3-access-key-id` / `s3-secret-access-key`, then create its schema `mirrors`. **Follow the Gravitino S3 Fileset catalog configuration already proven with your platform MinIO**, including the relevant filesystem bundle. Do not paste credentials into Git-tracked files.

Example catalog creation using environment variables (only on trusted host, avoid shell history storing secrets):

```bash
export GRAVITINO_URL=http://10.194.1.140:8090
# PLATFORM_S3_ACCESS_KEY and PLATFORM_S3_SECRET_KEY must already be exported securely.
jq -n \
  --arg access "$PLATFORM_S3_ACCESS_KEY" --arg secret "$PLATFORM_S3_SECRET_KEY" \
  '{name:"git_managed",type:"FILESET",comment:"Platform Git mirror",properties:{
    "location":"s3a://data-platform/metalake/git",
    "s3-endpoint":"http://10.194.1.140:9000",
    "s3-access-key-id":$access,"s3-secret-access-key":$secret,
    "credential-providers":"s3-secret-key"}}' | \
  curl --fail-with-body -sS -X POST \
    -H 'Accept: application/vnd.gravitino.v1+json' \
    -H 'Content-Type: application/json' \
    --data-binary @- "$GRAVITINO_URL/api/metalakes/ax/catalogs"
```

Then schema:

```bash
curl --fail-with-body -sS -X POST \
  -H 'Accept: application/vnd.gravitino.v1+json' \
  -H 'Content-Type: application/json' \
  --data '{"name":"mirrors","properties":{}}' \
  "$GRAVITINO_URL/api/metalakes/ax/catalogs/git_managed/schemas"
```

**Important:** A Gravitino MANAGED Fileset can delete managed data when dropped. Never drop a job Fileset while published jobs still reference its mirror.

## 3. Run Python Git worker on Linux 10.194.1.140

Copy `git_service/` from the ZIP to `/opt/meta-lake-git/` (copy its contents, not its parent directory):

```bash
mkdir -p /opt/meta-lake-git
cd /opt/meta-lake-git
# copy files from the ZIP's git_service/ here
python3.11 -m venv .venv
source .venv/bin/activate
python -m pip install -r requirements.txt
cp .env.example .env
chmod 600 .env
vi .env
python -m unittest -v test_main
python run.py
```

Required values: `METALAKE_GIT_SERVICE_TOKEN`, `GRAVITINO_URL`, `GRAVITINO_GIT_CATALOG=git_managed`, `GRAVITINO_GIT_SCHEMA=mirrors`, `PLATFORM_S3_ENDPOINT`, `PLATFORM_S3_ACCESS_KEY`, `PLATFORM_S3_SECRET_KEY`, `GIT_MIRROR_BUCKET=data-platform`, `GIT_MIRROR_PREFIX=metalake/git`, `ICEBERG_REST_URI=http://10.194.1.140:9001/iceberg`, `ICEBERG_NAMESPACE=ai_ready_poc`, and host allowlist. The worker needs `git` executable in PATH.

`GET http://10.194.1.140:8110/health` returns `{"status":"UP"}`; internal POST calls require matching `X-Internal-Token`.

If using a private Git repository, configure a read-only token in `GIT_TOKEN_<REF>` environment and send a `credentialRef`. Do not persist the token in Postgres or Postman. For public GitHub, no `credentialRef` is needed.

## 4. Postman API test

Import `postman/meta-lake-git-addon.postman_collection.json`, retaining your existing Vector v2 requests. Set `gitUrl`, `gitBranch`, `baseUrl`, `gitWorkerUrl`. Use folder **`02 Git Branch / Mirror / Iceberg`**:

1. `00 Git worker health` — Linux service is online
2. `01 List remote Git branches` — returns available branches
3. `02 Register Git Source with selected branch` — saves `gitSourceId`
4. `03 Get Git source` — confirms registration
5. `04 Clone + mirror + normalize Git branch` — saves `gitJobId`
6. `05 Git normalization job status` — polls `PENDING / RUNNING / SUCCEEDED / FAILED`
7. `06 Published Git outputs` — expects `CODE_FILES`, `CODE_SYMBOLS`, `CODE_RELATIONS` after success
8. `07 Gravitino mirrored Git fileset` — confirms named Fileset

Sample requests:

```http
POST {{baseUrl}}/api/git/branches
Content-Type: application/json

{"repositoryUrl":"https://github.com/km1031kim/meka-lake-worker.git","credentialRef":null}
```

```http
POST {{baseUrl}}/api/sources
Content-Type: application/json

{"name":"meka-lake-code","sourceType":"GIT","connection":{"repositoryUrl":"https://github.com/km1031kim/meka-lake-worker.git","ref":"main"}}
```

```http
POST {{baseUrl}}/api/sources/{{gitSourceId}}/normalizations
Content-Type: application/json

{"relativePaths":[]}
```

## 5. Inspect Iceberg from Trino

```sql
SHOW TABLES FROM iceberg_rest.ai_ready_poc;
SELECT processing_job_id, source_id, relative_path, language,
       substr(content, 1, 150) AS preview
FROM iceberg_rest.ai_ready_poc.code_files
WHERE processing_job_id = <YOUR_GIT_JOB_ID>
LIMIT 5;
SELECT symbol_type, qualified_name, relative_path, start_line
FROM iceberg_rest.ai_ready_poc.code_symbols
WHERE processing_job_id = <YOUR_GIT_JOB_ID>
LIMIT 10;
SELECT relation_type, source_symbol_id, target_symbol_id
FROM iceberg_rest.ai_ready_poc.code_relations
WHERE processing_job_id = <YOUR_GIT_JOB_ID>
LIMIT 10;
```

The code stores snapshot/output descriptors in PostgreSQL and `source_revision` as the actual 40-character Commit SHA. The same commit is mirrored under a job-specific immutable MinIO prefix, and all parsing reads **mirrored objects**, not mutable Git HEAD.

## 6. Current limitations

- Java-only AST structural extraction (`tree-sitter-java`); non-Java UTF-8 source files are indexed as `TEXT` in `code_files`, not deeply parsed.
- No Git LFS fetch, submodule materialization, incremental changes, or Git embedding in this POC.
- Managed mirror reads use `boto3` directly rather than OpenDAL/GVFS; this is a separate connector path.
- Single synchronous Git worker HTTP call; large repositories need an asynchronous queue/timeout handling.
- Iceberg writes and PostgreSQL publication are not one transaction. A failed job can leave unpublished Iceberg data and MinIO mirror objects; exclude them by `processing_job_id`.
- APIs should be protected with user authorization and Source ownership checks before multi-user deployment. Keep internal Git worker behind private network access.

## 7. IRC Credential Vending for code Iceberg tables (this package)

`write_iceberg()` uses `header.X-Iceberg-Access-Delegation: vended-credentials` and REST Catalog `ICEBERG_REST_URI`. It does **not** directly configure `s3.access-key-id` or `s3.secret-access-key` for Iceberg. Before end-to-end testing, verify IRC can vend credentials for existing tables as you already did with `documents`; for newly created `code_files`/`code_symbols`/`code_relations`, check that the client's PyIceberg version honors the CreateTable/LoadTable credentials. If the first write fails, inspect the Python worker log and the IRC CreateTable response before adding static credentials back.

`PLATFORM_S3_ACCESS_KEY` and `PLATFORM_S3_SECRET_KEY` are *still required* for direct S3 mirror upload/read using boto3. This is distinct from Iceberg storage. Avoid exposing these variables in HTTP or logs.

**Compatibility:** Java patch context was tested against Vector v2 + Lance Catalog synthetic code, not the user's latest Windows tree. Run `git apply --check` via the installer and stop rather than forcing any rejected patch.
