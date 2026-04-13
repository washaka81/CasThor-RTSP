import vertexai
from google.cloud import aiplatform

try:
    vertexai.init()
    print("✅ La conexión con Vertex AI se ha establecido correctamente.")
    print(f"Proyecto: {aiplatform.initializer.global_config.project}")
    print(f"Ubicación: {aiplatform.initializer.global_config.location}")
except Exception as e:
    print(f"❌ Ocurrió un error al intentar conectar con Vertex AI: {e}")

