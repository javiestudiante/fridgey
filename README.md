# Fridgey

**Aplicación móvil multiplataforma para reducir el desperdicio de comida mediante el seguimiento automatizado de fechas de caducidad.**

Fridgey permite registrar los productos de una o varias neveras —compartidas entre varios miembros—, escanear el código de barras y la fecha de caducidad de cada producto con la cámara, y recibir avisos antes de que los alimentos caduquen. Está desarrollada como Trabajo de Fin de Grado (TFG) del Grado en Ingeniería Informática de la Universidad de León.

Este documento explica cómo clonar el repositorio y compilar la aplicación en local, tanto en Android como en iOS, así como cómo aprovisionar la infraestructura de backend.

---

## Tabla de contenidos

1. [Arquitectura y tecnologías](#1-arquitectura-y-tecnologías)
2. [Estructura del proyecto](#2-estructura-del-proyecto)
3. [Requisitos previos](#3-requisitos-previos)
4. [Configuración de Firebase (obligatorio antes de compilar)](#4-configuración-de-firebase-obligatorio-antes-de-compilar)
5. [Compilar y ejecutar en Android](#5-compilar-y-ejecutar-en-android)
6. [Compilar y ejecutar en iOS](#6-compilar-y-ejecutar-en-ios)
7. [Infraestructura (Terraform / GCP)](#7-infraestructura-terraform--gcp)
8. [Solución de problemas frecuentes](#8-solución-de-problemas-frecuentes)

---

## 1. Arquitectura y tecnologías

Fridgey está construida con **Kotlin Multiplatform (KMP)**: la lógica de negocio, el acceso a datos y los casos de uso se comparten entre ambas plataformas en un único módulo común, mientras que cada plataforma implementa su propia interfaz de usuario con su framework nativo.

| Capa | Tecnología |
|------|-----------|
| Lógica compartida | Kotlin Multiplatform (KMP) |
| UI Android | Jetpack Compose |
| UI iOS | SwiftUI |
| Base de datos local | SQLDelight |
| Inyección de dependencias | Koin |
| Autenticación y backend | Firebase Authentication, Cloud Firestore |
| Cliente HTTP | Ktor + kotlinx.serialization |
| Reconocimiento (OCR + códigos de barras) | ML Kit (Android), Apple Vision (iOS) |
| Datos de productos | Open Food Facts API |
| Infraestructura | Terraform sobre Google Cloud Platform |

La autenticación se realiza mediante **Inicio de sesión con Google** (Android) e **Inicio de sesión con Apple** (iOS). El escaneo sigue un flujo secuencial: primero se lee el código de barras (que consulta Open Food Facts para autocompletar el producto) y a continuación se reconoce la fecha de caducidad mediante OCR.

---

## 2. Estructura del proyecto

```
fridgey/
├── shared/                 # Módulo Kotlin Multiplatform (lógica común)
│   └── src/
│       ├── commonMain/     # Código compartido: dominio, datos, casos de uso, SQLDelight
│       ├── androidMain/    # Implementaciones específicas de Android (expect/actual)
│       └── iosMain/        # Implementaciones específicas de iOS (expect/actual)
├── composeApp/             # Aplicación Android (Jetpack Compose)
├── iosApp/                 # Aplicación iOS (SwiftUI, proyecto Xcode)
├── infra/                  # Configuración de Terraform (GCP / Firebase)
├── gradle/
│   └── libs.versions.toml  # Catálogo de versiones de dependencias
└── README.md
```

> La ruta exacta de algunos directorios puede variar ligeramente respecto a la mostrada aquí. Lo esencial es: `shared` (común), `composeApp` (Android), `iosApp` (iOS) e `infra` (Terraform).

---

## 3. Requisitos previos

### Comunes (ambas plataformas)

- **JDK 17** o superior (recomendado el incluido con Android Studio).
- **Android Studio** (versión reciente, p. ej. Ladybug o posterior) con el plugin de Kotlin Multiplatform.
- **Git**.

### Solo para iOS

- **macOS** (la compilación de iOS solo es posible en Mac).
- **Xcode** (versión reciente compatible con el proyecto).
- **CocoaPods** (`sudo gem install cocoapods`), si el proyecto integra el framework compartido vía Pods.

### Solo para la infraestructura

- **Terraform** (versión 1.x).
- **Google Cloud SDK** (`gcloud`).
- Una cuenta de Google con permisos para crear un proyecto en Google Cloud Platform.

---

## 4. Configuración de Firebase (obligatorio antes de compilar)

> **Importante:** por seguridad, los archivos de configuración de Firebase contienen identificadores del proyecto y **normalmente están excluidos del control de versiones** (`.gitignore`). Si tras clonar **no encuentras** los archivos `google-services.json` o `GoogleService-Info.plist`, deberás aportarlos siguiendo los pasos de abajo. Si **sí están** incluidos en el repositorio, puedes saltarte esta sección.

Fridgey necesita un proyecto de Firebase con **Authentication** y **Cloud Firestore** habilitados. Tienes dos opciones:

- **Opción A — usar el proyecto existente del TFG:** solicita al autor los archivos de configuración (`google-services.json` y `GoogleService-Info.plist`) y colócalos donde se indica más abajo. Es la vía más rápida para evaluar la aplicación.
- **Opción B — crear tu propio proyecto de Firebase:** útil si quieres un backend independiente. Sigue estos pasos:

1. Entra en la [consola de Firebase](https://console.firebase.google.com/) y crea un proyecto nuevo.
2. Habilita **Authentication** y activa los proveedores **Google** (para Android) y **Apple** (para iOS).
3. Habilita **Cloud Firestore**.
4. Registra una **app de Android** con el `applicationId` del proyecto (revísalo en `composeApp/build.gradle.kts`) y descarga el archivo `google-services.json`.
5. Registra una **app de iOS** con el `bundle identifier` correspondiente y descarga el archivo `GoogleService-Info.plist`.

### Ubicación de los archivos

| Archivo | Plataforma | Dónde colocarlo |
|---------|-----------|-----------------|
| `google-services.json` | Android | En el directorio del módulo de la app Android (`composeApp/`) |
| `GoogleService-Info.plist` | iOS | En el directorio del proyecto Xcode (`iosApp/`), añadido al target de la app desde Xcode |

Sin estos archivos, la compilación de Android fallará en el paso del plugin `google-services`, y la app de iOS no podrá inicializar Firebase.

---

## 5. Compilar y ejecutar en Android

1. Clona el repositorio:

   ```bash
   git clone <URL-del-repositorio>
   cd fridgey
   ```

2. Coloca el archivo `google-services.json` (ver [sección 4](#4-configuración-de-firebase-obligatorio-antes-de-compilar)).

3. Abre el proyecto en **Android Studio** (selecciona la carpeta raíz). Espera a que Gradle sincronice las dependencias.

4. Conecta un dispositivo físico (con depuración USB activada) o arranca un emulador desde el **Device Manager**.

5. Selecciona la configuración de ejecución de la app Android y pulsa **Run**.

Alternativamente, desde la línea de comandos:

```bash
# Compilar el APK de depuración
./gradlew :composeApp:assembleDebug

# Instalar y ejecutar en el dispositivo/emulador conectado
./gradlew :composeApp:installDebug
```

> En Windows, usa `gradlew.bat` en lugar de `./gradlew`.

El escáner de códigos de barras y el OCR requieren **cámara**, por lo que la funcionalidad de escaneo solo se puede probar por completo en un dispositivo físico (los emuladores no tienen cámara real).

---

## 6. Compilar y ejecutar en iOS

> La compilación de iOS requiere **macOS con Xcode**.

1. Asegúrate de haber clonado el repositorio y de tener instaladas las herramientas de la [sección 3](#3-requisitos-previos).

2. Coloca el archivo `GoogleService-Info.plist` y añádelo al target de la app desde Xcode (ver [sección 4](#4-configuración-de-firebase-obligatorio-antes-de-compilar)).

3. Si el proyecto usa CocoaPods, instala las dependencias:

   ```bash
   cd iosApp
   pod install
   ```

4. Abre el proyecto en Xcode:
  - Si hay un archivo `.xcworkspace` (caso con CocoaPods), **abre el `.xcworkspace`**, no el `.xcodeproj`.
  - Si no, abre el `.xcodeproj`.

5. En Xcode, selecciona un simulador o un dispositivo físico y pulsa **Run** (▶).

> **Nota sobre el inicio de sesión:** el Inicio de sesión con Apple requiere una cuenta de Apple ID configurada en el dispositivo/simulador. Como el escaneo necesita cámara, el flujo completo de escaneo solo se puede verificar en un **iPhone físico**.

Si modificas código del módulo compartido (`shared`), el framework de Kotlin para iOS se regenera automáticamente al compilar desde Xcode, ya que Gradle se ejecuta como parte del build. La primera compilación puede tardar varios minutos.

---

## 7. Infraestructura (Terraform / GCP)

El backend de Fridgey se aprovisiona de forma reproducible con **Terraform** sobre **Google Cloud Platform / Firebase**. El alcance de la configuración se mantuvo deliberadamente acotado: Terraform gestiona **Firestore, Identity Platform, los índices y las reglas de seguridad**, mientras que la configuración de OAuth (Google) e Inicio de sesión con Apple se realiza manualmente desde la consola de Firebase (una decisión pragmática justificada por la complejidad de automatizar esos pasos).

> Esta sección solo es necesaria si quieres **recrear el backend desde cero** en tu propio proyecto de GCP. Para únicamente compilar y probar la app contra un backend existente, basta con la [sección 4](#4-configuración-de-firebase-obligatorio-antes-de-compilar).

### Pasos

1. Instala [Terraform](https://developer.hashicorp.com/terraform/install) y el [Google Cloud SDK](https://cloud.google.com/sdk/docs/install).

2. Autentícate con credenciales de aplicación por defecto:

   ```bash
   gcloud auth application-default login
   ```

3. Crea un proyecto en Google Cloud (o usa uno existente) y anota su **ID de proyecto**.

4. Entra en el directorio de infraestructura:

   ```bash
   cd infra
   ```

5. Inicializa Terraform:

   ```bash
   terraform init
   ```

6. Revisa el plan de cambios, indicando el ID de tu proyecto. Según cómo estén definidas las variables, se pasan así:

   ```bash
   terraform plan -var="project_id=TU_ID_DE_PROYECTO"
   ```

7. Aplica la configuración:

   ```bash
   terraform apply -var="project_id=TU_ID_DE_PROYECTO"
   ```

8. Completa manualmente desde la [consola de Firebase](https://console.firebase.google.com/) la parte no gestionada por Terraform:
  - Configuración del proveedor de **Inicio de sesión con Google** (OAuth).
  - Configuración del proveedor de **Inicio de sesión con Apple**.

> Los nombres exactos de las variables (`project_id`, región, etc.) están definidos en los archivos `.tf` del directorio `infra/`. Consúltalos antes de ejecutar `plan`/`apply` por si difieren de los mostrados aquí.

---

## 8. Solución de problemas frecuentes

| Problema | Causa probable | Solución |
|----------|---------------|----------|
| La compilación de Android falla en el paso `google-services` | Falta `google-services.json` | Colócalo en `composeApp/` (ver sección 4) |
| La app iOS arranca pero falla al iniciar sesión / conectar con Firebase | Falta `GoogleService-Info.plist` o no está añadido al target | Añádelo desde Xcode al target de la app |
| `INSTALL_FAILED_INSUFFICIENT_STORAGE` al instalar en el emulador | El emulador se quedó sin espacio | Libera espacio o crea un emulador con más almacenamiento |
| El escáner muestra la cámara pero no detecta nada | Estás en un emulador sin cámara | Prueba en un dispositivo físico |
| Gradle no sincroniza o falla por versión de JDK | JDK incorrecto | Usa JDK 17 (configúralo en *Settings → Build Tools → Gradle → Gradle JDK*) |
| En iOS, los cambios del módulo `shared` no se reflejan | Caché de build | Limpia el build en Xcode (*Product → Clean Build Folder*) y recompila |
| `pod install` falla | CocoaPods desactualizado | Ejecuta `pod repo update` y reintenta |

---

## Licencia y autoría

Proyecto desarrollado como Trabajo de Fin de Grado (TFG) del Grado en Ingeniería Informática de la **Universidad de León**.