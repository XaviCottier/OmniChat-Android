# OmniChat Android — APK source (v0.1)

Chat Android sin servidor intermedio. El dispositivo envía mensajes directamente a la API seleccionada. Código Java + WebView local, sin librerías de terceros ni trackers.

## Funciones incluidas

- Añadir, editar y eliminar proveedores; plantillas OpenRouter, OpenAI, DeepSeek, Groq, Cerebras, Mistral, Together AI, Anthropic, Gemini, Ollama.
- URL base propia; ruta de modelos y ruta de chat configurables para OpenAI-compatible; cabeceras JSON personalizadas con `${API_KEY}`, modo de autenticación, parámetros de tokens/temperatura y opción de desactivar streaming.
- Protocolos OpenAI Chat Completions, Anthropic Messages, Gemini generateContent y Ollama chat.
- Catálogo de modelos cuando el proveedor expone `/models` o `/api/tags`; ID manual si el catálogo no funciona.
- Chat con respuesta incremental (SSE / NDJSON), varias conversaciones, cambio de proveedor y modelo sin eliminar el contexto guardado, instrucción de sistema, temperatura, límite de tokens, cancelar y copiar respuesta.
- Distinción de errores HTTP 400/401/402/403/404/429/5xx. Cuota, facturación y acceso los decide cada proveedor.
- API keys, cabeceras y chats cifrados en almacenamiento privado con AES-256-GCM y Android Keystore. Backup desactivado. No se almacenan claves dentro del código o repositorio.
- Exportar un chat mediante el menú compartir Android.

## Compilar APK

1. Subir el **contenido** de este proyecto a un repositorio GitHub propio nuevo (no subir claves API).
2. GitHub → **Actions** → **Build Android APK** → **Run workflow**; también se ejecuta al enviar cambios a `main`/`master`.
3. Abrir la ejecución → **Artifacts** → `OmniChat-debug-apk`; el ZIP contiene `app-debug.apk`.

Alternativa local: Android Studio con JDK 17 y Android SDK 35; abrir proyecto y compilar `:app:assembleDebug` con Gradle 8.9. APK: `app/build/outputs/apk/debug/app-debug.apk`.

El APK debug se firma automáticamente con clave de depuración de Android. Para distribución a terceros, generar y custodiar una clave de firma release propia.

## Uso

1. ⚙ → Nuevo proveedor → elegir plantilla o protocolo, URL base y API key, guardar.
2. Elegir proveedor; pulsar ↻ para consultar catálogo, o escribir directamente el ID del modelo.
3. Enviar mensajes. El mismo chat puede continuar con otro proveedor/modelo usando el contexto textual.
4. Para Ollama desde un teléfono real, reemplazar `127.0.0.1` por la IP LAN del servidor Ollama, configurarlo para escuchar en la red y no exponerlo a Internet.

## Alcance y limitaciones

- **No existe un protocolo universal para todas las APIs.** Soporta cuatro familias; para proveedores incompatibles habrá que implementar un adaptador. La ruta custom cubre APIs que aceptan formato OpenAI Chat Completions.
- "Conexión mediante cuenta" significa usar la API key o token emitido por la cuenta de ese proveedor. No integra OAuth/login web propietario. Pagar ChatGPT Plus, Claude Pro u otra suscripción web no implica crédito API.
- Esta versión es **chat de texto**; no incorpora archivos, visión, audio, herramientas/function calling, agente ni ejecución de código. La disponibilidad de funciones, límites y coste depende del modelo y cuenta.
- Algunos modelos rechazan `temperature` o `max_tokens` o no ofrecen streaming; usa las opciones avanzadas (`max_completion_tokens`, sin temperatura, streaming desactivado). Otros formatos de solicitud aún requieren adaptar código.
- Se conservan todos los mensajes localmente, pero se envían al modelo la instrucción de sistema y los **últimos 40 mensajes** para acotar contexto/coste.
- El almacenamiento cifra chats y credenciales, pero el texto necesariamente se descifra en memoria para mostrarlo y enviarlo al proveedor. Compartir un chat exporta texto sin cifrar voluntariamente.
- HTTP sin cifrar se admite **solo sin clave ni cabeceras personalizadas**, orientado a Ollama en LAN. Las API keys requieren HTTPS. No instalar desde orígenes desconocidos sin revisar firma/código.
- Los modelos disponibles se enumeran como catálogo; **no se garantiza autorización, cuota ni funcionamiento hasta enviar una petición real**.
- API keys no se envían a ningún servidor de OmniChat. La app no solicita contraseñas de cuentas de sitios web.

## Archivos

- `app/src/main/java/com/omnichat/app/MainActivity.java`: puente Android UI, proveedores, cifrado y persistencia.
- `ApiClient.java`: peticiones HTTP, adaptadores y streaming.
- `SecureStore.java`: Android Keystore + AES/GCM.
- `app/src/main/assets/`: interfaz local (HTML, CSS, JS).
- `.github/workflows/build-apk.yml`: compilación APK en GitHub Actions.

Proyecto independiente. No incorpora código ni recursos de CuadroFlow.
