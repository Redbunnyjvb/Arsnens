# Vervolgaudit: camerabeweging, trage correctie en plaatsen zonder zichtbare tag

Deze audit vervolgt de meldingen van 8–9 september: de paarse pose blijft bestaan terwijl sensorplaatsing blokkeert, de correctie duurt lang en ‘live’ en ‘fused’ lopen tijdens camerabeweging uiteen. De bevindingen zijn vastgesteld in de code en met regressies. Er is geen nieuwe opname waarmee de resterende fysieke afwijking op het toestel kan worden gemeten.

## Vastgestelde oorzaken en wijzigingen

### P1 — Sensorplaatsing verliep na tien seconden zonder bevestigende tag

De fusie hield de transform en schermprojectie vast, maar schakelde na tien seconden naar `DriftPossible`. De plaatsingsbeslissing blokkeerde die status. Een zichtbare overlay betekende daardoor niet dat de operator verder kon plaatsen.

De ARCore-route gebruikt nu één native `Session.createAnchor()` bij het waargenomen referentiegebied. Cameraposes, model, cursor en bewaarde plaatsingsstralen worden in het lokale frame van dat anker verwerkt. Camera en anker worden iedere frame op tracking gecontroleerd. De ARCore-route blijft plaatsing toestaan zolang beide volgen en de objectuitlijning bruikbaar is; de leeftijd van de laatste bevestiging blijft zichtbaar in de kwaliteit. Het tiensecondenbeleid blijft alleen voor de ongebonden, oude wereldframe-route in de fusie beschikbaar.

Een native anker dat `PAUSED` is, blokkeert plaatsing totdat tracking terugkeert. Bij `STOPPED`, gewijzigde referentiegeometrie, gewijzigde posemodus/tagfamilie of expliciet opnieuw ijken wordt het anker vervangen en is kalibratie nodig. Elk nieuw trackingframe krijgt een uniek ID. Analysepakketten en bewaarde stralen uit een oud frame worden niet in het nieuwe frame gebruikt. Het anker wordt bij vervanging en het sluiten van de AR-route losgelaten.

Dit vervangt de openstaande native-ankergrens uit de audit van 8 september. Het is een sessieanker; er is hiermee geen kalibratie tussen appstarts opgeslagen.

### P1 — Camerabeweging en cursorbeweging werden als plaatsingsverbod gebruikt

De vorige blokkering gebruikte beweging tussen opname en verwerking van de laatste tagmeting (>30 mm of >3°). Dat is geen actuele meting van beeldonscherpte. De fusie gebruikt al de camera van de opname om die meting in het trackingframe te plaatsen. Bovendien bevat de gemeten cursorvariatie ook het bewust richten op een andere plaats.

Deze signalen blokkeren de actuele AR-cursor niet meer. Ze blijven informatief en tellen mee voor de strengere High-kwaliteitsindicatie. Een bruikbare, gevolgde kalibratie kan Medium-kwaliteit houden tijdens het richten. Onjuiste referenties, onvoldoende posekwaliteit, een nog niet bruikbare correctie en werkelijk trackingverlies blijven blokkeren. Een fysieke sensor-tag moet uiteraard in een recente opname staan om zijn eigen pixelpositie te meten; handmatig plaatsen met de AR-cursor vereist dat niet.

### P1 — Oude initialisatiemetingen vervuilden nieuwe correcties

Na acceptatie werd de wachtende metingenreeks niet geleegd. In een regressie met een gevestigde pose op 0 mm en daarna drie correcte metingen op 24 mm bleef de correctie op slechts 9 mm steken: het oude gemiddelde telde mee bij het nieuwe doel. Dit is een echte correctiefout, naast het weergaveprobleem hieronder.

Een geaccepteerde initialisatie of correctie sluit nu zijn meetvenster af. De volgende correctie moet eigen onafhankelijke waarnemingen opbouwen. Normale correcties vereisen minimaal drie metingen over 200 ms; initialisatie minimaal vijf over 400 ms. Grotere correcties met meerdere overeenkomende referenties blijven strenger gecontroleerd. Eén sterk afwijkende referentie mag een gevestigd model niet onmiddellijk verplaatsen.

### P1 — Een bevestigde correctie wachtte op volgende tagbeelden om af te ronden

Voorheen schoof het anker alleen een fractie op bij een geaccepteerd analysepakket. Als de tag daarna verdween, kon het anker halverwege blijven staan en bleef de plaatsingsstatus onklaar.

Een bevestigde correctie heeft nu een eigen doel en loopt per renderframe verder met een tijdconstante van 150 ms. Deze voortgang betreft alleen de objectuitlijning; de actuele camerabeweging wordt niet afgevlakt. Trackingpauze stopt het bijstellen. Een conflict of afgewezen grote sprong annuleert de lopende correctie. Onbevestigde waarnemingen worden niet door alleen het verstrijken van tijd geldig.

### P2 — ‘Live’ en ‘fused’ vergeleken verschillende cameramomenten

De cyaan gemeten taghoeken waren schermpixels van het opgenomen analysebeeld, tot 500 ms op het scherm gehouden. De paarse fused route gebruikte ondertussen de actuele camera. Ook de foutstatistieken vergeleken deze verschillende momenten zolang de detectie jonger dan 250 ms was. ‘Vers’ betekent hier niet ‘hetzelfde cameramoment’. Bewegen kon daardoor een grote schijnbare afwijking opleveren.

Bekende, vers gemeten referentietaghoeken worden nu via hun **eigen gemeten tagvlak** en de camerabeweging naar de huidige weergave geprojecteerd. De fused objectpose is geen input van deze omzetting. Een werkelijke fout blijft dus zichtbaar. Deze route heet ‘gemeten’; ruwe referentiebeelden zonder bruikbare pose krijgen een expliciete opnameleeftijd. Oude referentiemetingen worden na 250 ms niet meer als actuele waarneming doorgeprojecteerd.

De gecombineerde kandidaatpose volgt eveneens iedere frame de huidige camera, ook tussen analysepakketten. De gele projectie uit de opnamecamera wordt niet meer naast een actuele AR-projectie als gelijkwaardig huidig beeld getekend. De ruwe hoeken blijven ongewijzigd beschikbaar voor metingen en logging.

Diagnostiek vergelijkt nu:

- `maxDeltaFusedVsCapture`: de fused uitlijning in de opnamecamera tegenover de hoeken van die opname;
- `maxDeltaCurrentFusedVsTracked`: fused tegenover de naar dezelfde huidige camera overgebrachte waarneming;
- `anchorImageErrorPx`: maximale oorspronkelijke hoekfout over alle gebruikte referenties, bij de opnamecamera.

## Gecontroleerde transformatieketen

Noem het lokale native-ankerframe F, de trafo T en de fysieke opnamecamera C. De gemeten objectuitlijning is:

`F_from_T = F_from_CaptureGL × GL_from_CV × CV_from_T_measured`

De schermprojectie gebruikt vervolgens de actuele, display-georiënteerde view:

`clip_from_T = projection × CurrentView_from_F × F_from_T`

De controle tegen beeldhoeken gebruikt juist `CaptureCV_from_F × F_from_T`. De fysieke camera-assen en de display-rotatie worden niet verwisseld. Dit volgt de conventies uit de [ARCore camera-documentatie](https://developers.google.com/ar/reference/java/com/google/ar/core/Camera). `acquireCameraImage()` levert het beeld behorend bij de huidige Frame; dat beeld, de intrinsics en opnamecamera worden samen gekopieerd voor de asynchrone detector. Zie [ARCore Frame](https://developers.google.com/ar/reference/java/com/google/ar/core/Frame).

Eén gedeeld native anker volgt het advies om gerelateerde objecten aan hetzelfde anker te koppelen, zodat wereldverfijningen hun onderlinge samenhang behouden. Zie [ARCore ankers](https://developers.google.com/ar/develop/anchors). Dat garandeert geen absolute nauwkeurigheid rondom een volledige trafo.

## Verificatie en grenzen

De regressies voeren de productiecode voor fusie, projectie en plaatsingsbeslissing uit. Nieuwe dekking omvat drie minuten zonder zichtbare tags, plaatsing na normale camerabeweging, hervatten na trackingpauze, afronden van een bevestigde correctie zonder nieuwe tagbeelden en correctie terwijl de opnamecamera beweegt.

De debugprojectieregressies vergelijken verschillende camerastanden tussen analysepakketten, inclusief rotatie van de displayview. Een aparte test geeft de gemeten referentie bewust 24 mm afwijking: de verwachte 40 px verschil met fused blijft zichtbaar. Daarmee wordt voorkomen dat de nieuwe debugweergave een werkelijke kalibratiefout cosmetisch verbergt. De bestaande tests voor kleine-tagruis, verschoven referenties, conflicten en het niet hergebruiken van analysepakketten blijven van toepassing.

Definitieve uitvoering: **160 JVM-tests geslaagd, nul mislukkingen, nul fouten, nul overgeslagen tests**. `testDebugUnitTest`, `assembleDebug` en `assembleDebugAndroidTest` zijn geslaagd met de lokale offline Gradle-cache. De nieuwe app-APK staat in `app/build/outputs/apk/debug/app-debug.apk`. `git diff --check` meldt geen whitespacefouten.

ADB toont geen verbonden toestel. De native Android-tests kunnen hier alleen worden gebouwd. De APK is niet op de telefoon geïnstalleerd. De echte camera, motion blur, rolling shutter, Compose/GL-weergavevertraging en langdurige ARCore-kaartverfijning blijven toesteltests vereisen. Een kleine tag of onjuist ingemeten taggeometrie kan ondanks een rustig beeld een verkeerde absolute pose geven.

## Praktische toestelcontrole

1. Kalibreer op correct ingemeten referenties, richt op een andere plek en plaats daar met de cursor, ook na ruim tien seconden zonder referentietag in beeld.
2. Beweeg zijwaarts en draai de telefoon terwijl een tag zichtbaar blijft. Vergelijk ‘gemeten’ en ‘fused’ en de fout bij de opnamecamera. Alleen de ruwe opnamecontour mag zijn gemeten leeftijd tonen.
3. Breng gecontroleerde referenties terug in beeld. Een bevestigde kleine correctie hoort af te ronden, ook als de tags kort daarna verdwijnen.
4. Controleer trackingpauze en terugkeer, en apart een verschoven tag tussen meerdere goede referenties. Een inconsistentie moet herkenbaar blijven en mag niet worden weggemiddeld.
