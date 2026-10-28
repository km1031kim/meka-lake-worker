"""Launch Git worker using .env. Run inside git_service/ virtualenv."""
import os
from dotenv import load_dotenv
import uvicorn
load_dotenv()
if not os.environ.get("METALAKE_GIT_SERVICE_TOKEN"):
    raise SystemExit("METALAKE_GIT_SERVICE_TOKEN must be configured")
uvicorn.run("main:app", host=os.getenv("GIT_BIND_HOST", "0.0.0.0"),
            port=int(os.getenv("GIT_PORT", "8110")), workers=1)
