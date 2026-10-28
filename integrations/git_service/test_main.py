import os
import unittest
from unittest.mock import patch
import main

class GitUnitTests(unittest.TestCase):
    def test_validate_host_url_and_branch(self):
        with patch.dict(os.environ, {"GIT_ALLOWED_HOSTS": "github.com,gitlab.example.com"}):
            self.assertEqual(main.validate_repository("https://github.com/demo/sample.git", "main"),
                             "https://github.com/demo/sample.git")
            for uri in ("file:///etc/passwd", "ssh://github.com/demo/r.git", "https://evil.com/r.git",
                        "https://u:p@github.com/r.git", "https://github.com/a/../b"):
                with self.assertRaises(ValueError): main.validate_repository(uri)
            for branch in ("../main", "foo//bar", "-master", "bad.lock"):
                with self.assertRaises(ValueError): main.validate_repository("https://github.com/a/b", branch)

    def test_credential_ref_does_not_embed_token_in_url(self):
        with patch.dict(os.environ, {"GIT_TOKEN_READER": "test-secret"}):
            env = main.git_env("reader")
            self.assertEqual(env["GIT_CONFIG_COUNT"], "1")
            self.assertIn("Authorization: Basic", env["GIT_CONFIG_VALUE_0"])
            self.assertEqual(env["GIT_ALLOW_PROTOCOL"], "https")

if __name__ == "__main__": unittest.main()

class GitSecurityAndMirrorTests(unittest.TestCase):
    def test_internal_token_required(self):
        from fastapi import HTTPException
        with patch.dict(os.environ, {"METALAKE_GIT_SERVICE_TOKEN": "secret-token"}):
            with self.assertRaises(HTTPException): main.require_token(None)
            with self.assertRaises(HTTPException): main.require_token("wrong")
            self.assertIsNone(main.require_token("secret-token"))
        with patch.dict(os.environ, {"METALAKE_GIT_SERVICE_TOKEN": ""}):
            with self.assertRaises(HTTPException): main.require_token(None)

    def test_job_specific_mirror_uri(self):
        with patch.dict(os.environ, {"GIT_MIRROR_BUCKET": "data-platform", "GIT_MIRROR_PREFIX":"metalake/git"}):
            uri1=main.mirror_location(12, 8, "a"*40)
            uri2=main.mirror_location(12, 9, "a"*40)
            self.assertNotEqual(uri1, uri2)
            self.assertEqual(uri1,"s3a://data-platform/metalake/git/source-12/commits/"+"a"*40+"/job-8/")

    def test_mirror_read_path(self):
        class Client:
            def get_object(self, **kwargs):
                self.kwargs=kwargs
                from io import BytesIO
                return {"Body":BytesIO(b"hello")}
        client=Client()
        out=main.read_mirrored_file(client,"s3a://bucket/foo/job-5/","src/A.java")
        self.assertEqual(out,b"hello")
        self.assertEqual(client.kwargs,{"Bucket":"bucket","Key":"foo/job-5/src/A.java"})
