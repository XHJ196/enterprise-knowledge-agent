from fastapi import FastAPI

app = FastAPI(title="Enterprise Knowledge AI Service",version="1.0.0")

@app.get("/health")

def health():
    return{"status":"Up"}