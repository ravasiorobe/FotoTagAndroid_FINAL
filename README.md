# FotoTag Android | progetto per APK via GitHub Actions

Versione prototipo 0.1: un file JPEG alla volta, tag multipli XMP `dc:subject`, modifica del file scelto dopo conferma. Non richiede accesso a tutta la libreria né account cloud.

## Compilazione senza Android Studio

1. Crea un repository GitHub **privato** vuoto.
2. Estrai `FotoTagAndroid_FINAL.zip` su PC.
3. Nella pagina del repository usa **uploading an existing file** e trascina **insieme le cartelle** `.github`, `app`, `tests` e i file `README.md`, `settings.gradle`, `build.gradle`, `gradle.properties`. **Attenzione:** la cartella `.github` è nascosta su alcuni sistemi e il caricamento GitHub tramite browser può non mantenere cartelle annidate. Prima del commit verifica che GitHub elenchi `.github/workflows/build.yml`.
4. Premi **Commit changes**. Nella scheda **Actions** cerca `FotoTag - test e APK`.
5. Quando l'azione è verde, aprila e scarica `FotoTag-APK` dalla sezione Artifacts: all'interno si trova `app-debug.apk`.
6. Installa l'APK sul Samsung autorizzando l'origine dell'installazione. Usa prima **una copia di prova** di una foto JPEG.

## Verifiche svolte e limiti

- Test del motore Java eseguito localmente (scrittura XMP, rilettura, 200+ tag, Unicode/XML, aggiornamento, input non JPEG).
- Workflow include lo stesso test prima della compilazione Gradle 8.9 + Android Gradle Plugin 8.7.3.
- **Non è stato possibile compilare né installare un APK in questo ambiente**, privo di Android SDK e Gradle. La riuscita della build GitHub non può essere certificata prima dell'esecuzione remota.
- I tag sono XMP `dc:subject`: **non è garantita l'indicizzazione da parte della Galleria Samsung**. La compatibilità va provata sul Samsung con una copia JPEG.
- La modifica dell'originale passa da Android Storage Access Framework e dipende dalle autorizzazioni in scrittura concesse dal provider scelto. In caso di errore il programma tenta il ripristino, che non è garantito. Fai un backup.
- JPEG soltanto. Il codice rifiuta XMP esteso e non sincronizza i campi legacy IPTC / EXIF XPKeywords.

Il prototipo non è una versione di produzione e non va usato senza backup delle immagini.
