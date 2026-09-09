# E2E-audit: zichtbare tag, maar sensorplaatsing valt weg

**Vervolg op 9 september:** [camerabeweging en plaatsing zonder tags](2026-09-09-camera-motion-and-tag-free-placement.md) beschrijft aanvullend herstel van het tiensecondenverbod, de native ARCore-ankerintegratie, vertraagde correcties en de vergelijking tussen opnamepixels en actuele fused projectie. De onderstaande native-ankergrens en het resetgedrag bij trackingverlies beschrijven de eerdere versie; de vervolgaudit geeft het huidige gedrag.

Vervolgaudit op de opname van **8 september 2026, 21:40:03.655–21:40:05.451**. Dit document vervangt voor dit probleem de eerdere aannames over ankerstabilisatie. De opname bevat geen ruwe taghoeken of rotatievectoren: de precieze fysieke pose kan er niet achteraf uit worden gereconstrueerd.

## Conclusie uit de log

De detector verliest de tag in dit fragment niet. Er staan **29 geverifieerde poses van tag 0**, met fitfouten van ongeveer **0,16–0,49 px**. De opgeslagen referentie is **47 mm, centrum (3933, 0, 0) mm**. Bij alle volledige fusieregels blijft `tracking=true`; de analyseleeftijd is **33–67 ms**.

De fusieregels tonen twee acceptaties, zeven afwijzingen wegens `reference-jump` en zeven overgangen naar `anchor-settling`. Dit zijn gelogde beslissingen, geen telling van alle cameraframes. Om 21:40:03.667 is een correctie geaccepteerd; één milliseconde later wordt sensor 4 opgeslagen. Om 21:40:03.715 — slechts 48 ms na de acceptatie — trekt een afgewezen tagpose de stabiliteitsstatus weer in. Om 21:40:05.156 zijn `transformerPose=true` en `displayProjection=true` nog aanwezig.

**De aantoonbare hoofdoorzaak is de afhandeling ná detectie: een bruikbaar gepubliceerd anker en de beoordeling van één nieuwe posemeting waren aan elkaar gekoppeld.** De gebruiker ervaart dat als poseverlies, terwijl het anker in dit fragment blijft bestaan en vooral de plaatsingsmogelijkheid wegvalt. De boodschap om de telefoon rustig te houden verklaarde de echte blokkering niet. De recente filterwijziging introduceerde deze regressie; de eerdere tests dekten dit patroon onvoldoende.

## Bevindingen en herstel

### P1 — Een losse meting trok de kalibratie direct in (hersteld)

`AnchorPoseFilter.update()` gaf bij iedere pending of afgewezen pose `settled=false`. De fusie kopieerde dit direct naar `anchorSettled`. Bovendien maakte de reden `reference-jump` de trackingstatus onmiddellijk `NeedsRecalibration`. De UI eiste een stabiel anker en minimaal Medium-kwaliteit om een sensor vast te leggen.

Daarmee kon de plaatsingsknop tussen twee beeldanalyses verdwijnen, zelfs wanneer het bestaande anker onveranderd bleef staan en ARCore correct trackte.

De beschikbaarheid beschrijft nu het **gepubliceerde anker**. Eén onbruikbare nieuwe meting verplaatst het anker niet en trekt de bestaande kalibratie niet onmiddellijk in. Afwijkingen die minimaal vijf waarnemingen en één seconde aanhouden, leiden wel tot herkalibratie. Een reeds vastgesteld conflict tussen meerdere referenties blijft actief totdat een betrouwbare meting de uitlijning bevestigt; tags uit beeld laten verdwijnen heft dat conflict niet op.

### P1 — Goede hoekfit werd verward met een nauwkeurige 3D-rotatie (hersteld in de updatebeslissing)

De oorspronkelijke grenzen waren 30 mm/1,5° tegenover een gevestigd anker en 1° tegenover een wachtende pose. Bij een kleine vlakke tag kunnen lage beeldfouten samengaan met sterk wisselende rotaties. IPPE levert meerdere mogelijke oplossingen; een kleine fitfout alleen maakt de volledige 3D-oriëntatie niet nauwkeurig.

De nieuwe `anchorImageErrorPx()` controleert het bestaande anker rechtstreeks tegen **alle vier de gemeten hoeken van iedere gebruikte referentietag**, met de camerageometrie van de opname. Het maximum telt; een slechte hoek of tag verdwijnt niet in een gemiddelde. Als het bestaande anker op alle hoeken binnen 1,5 px past, wordt de kalibratie bevestigd zonder het anker te verplaatsen (`anchor-image-held`). De nieuw berekende, onrustige 3D-pose hoeft dan niet gevolgd te worden.

Dit is een consistentiecontrole, geen nauwkeurigheidsclaim. Bij onvoldoende overeenstemming blijven de pose-, consensus-, sprong- en stabilisatiecontroles van toepassing.

### P1 — Eerste kalibratie kon steeds opnieuw beginnen (hersteld)

Een grens op de rotatie tussen opeenvolgende poses kan de wachttijd voortdurend resetten, terwijl de taghoeken nauwelijks bewegen. In de nieuwe ruisregressie duurde de eerste kalibratie aanvankelijk 2,9 seconde vanaf de eerste meting, ondanks geldige beelden. De test faalde daarop.

Tijdens initialisatie wordt daarom ook de wachtende ankerhypothese tegen het nieuwe opgenomen beeld gecontroleerd. Een passende hypothese wordt niet alleen door de onzekere rotatie van de nieuwe PnP-oplossing weggegooid. Initialisatie blijft minimaal vijf onafhankelijke metingen en 400 ms vereisen. Middeling vindt plaats rond de waargenomen referentie, zodat de verafgelegen projectoorsprong het tagcentrum niet verplaatst.

Ook het wisselen tussen één en meerdere zichtbare referenties begint de initialisatie niet opnieuw wanneer de wachtende hypothese op alle nieuwe referentiehoeken past. Zonder die beeldbevestiging blijven wijzigingen van de referentiegroep de stabilisatie opnieuw starten.

### P2 — De temporele voorkennis gebruikte de vorige camera (hersteld)

De IPPE-keuze gebruikte een vorige tagpose in cameracoördinaten, zonder daarin de echte camerabeweging te verwerken. Bij snel scannen kan die vorige camerastand een verkeerde voorkeur opleveren.

De AR-route geeft nu een voorspelde pose mee in de **camera van de nieuwe opname**, afgeleid van het wereldanker en de ARCore-camerapose. Die voorspelling en de gekozen posemodus worden met het analysepakket vastgelegd. De beeldfit blijft bepalen of een alternatieve oplossing acceptabel is.

### P2 — Diagnose, logging en testdekking (hersteld)

- De knop en de opslagactie gebruiken dezelfde plaatsingsbeslissing met een concrete reden: initialisatie, correctie, referentieconflict, oude kalibratie of echte camerabeweging.
- Fusieregels bevatten nu ook ankerfit in pixels, positie-/hoekverschil en sampleaantal.
- De twee afwisselende soorten detectorlogs omzeilden elkaars throttle. Throttling gebeurt nu per categorie.
- Oude/herhaalde analysevolgnummers leveren geen nieuwe kalibratie-informatie op.
- De bestaande native testfixture ontbrak beeldbreedte en -hoogte. Daardoor zou zijn projectiehelper null teruggeven. De fixture is gecorrigeerd; alleen het bouwen ervan was eerder geen bewijs dat de test werkte.
- Matrix/rotatievectorconversie gebruikt nu native-vrije rigide meetkunde. Daardoor worden de werkelijke fusie, projectie en plaatsingsbeslissing in JVM-tests uitgevoerd; er hoeft geen succesvolle ankerstatus te worden nagebootst. Rotaties rond nul, 180° en willekeurige assen zijn afzonderlijk gecontroleerd.

## Controle van de volledige route

| Stap | Resultaat van de audit |
|---|---|
| Camera → opname | Camera/intrinsics/beeldmapper worden bij dezelfde opname vastgelegd. Deze log toont geen stale-detectionprobleem. |
| AprilTag-detectie | Tag 0 wordt herhaaldelijk herkend. Geen onbekende ID, uitgeschakelde tag of verdwenen detectie in dit fragment. |
| Hoekvolgorde en geometrie | Buitenaanzichten blijven gelijk aan de eerdere geometriecontrole. De log bewijst niet dat de fysieke printmaat, rotatie en centrum-XYZ correct zijn ingemeten. |
| PnP en meerdere tags | Individuele en gezamenlijke oplossingen blijven op volledige tags getoetst. De fout zat hier vooral in de verwerking van enkel-tagrotatieruis. |
| Capture → AR-wereld | De camera bij opname bepaalt de gemeten wereldpose; de actuele camera bepaalt de schermprojectie. Voorspelling en beeldvalidatie gebruiken nu expliciet de juiste opnamecamera. |
| Ankerupdate | Passende bestaande projectie wordt bevestigd. Een afgewezen meting wordt onderscheiden van een onbruikbaar anker. |
| Overlay | Model en sensoren delen de gefuseerde transformatie. In de ontvangen log bleef de projectie bestaan. |
| Cursor en sensor-tag | De cursor gebruikt de actuele projectie; een sensor-tagpixel blijft gekoppeld aan zijn opnamecamera en het gekozen trafovlak. |
| Plaatsingsknop en opslaan | Eén gedeelde beslisfunctie voorkomt verschillende voorwaarden en geeft de werkelijke blokkering weer. |
| Reset/trackingverlies | Bewuste herkalibratie en gewijzigde referentiegeometrie resetten de uitlijning. Werkelijk ARCore-trackingverlies verbergt de overlay tot nieuwe kalibratie; het wordt niet als een losse taguitschieter behandeld. |

## Verificatie

Definitieve uitvoering: **151 JVM-tests geslaagd, nul fouten, nul mislukkingen, nul overgeslagen tests**. `testDebugUnitTest`, `assembleDebug` en `assembleDebugAndroidTest` zijn geslaagd. De nieuwe app-APK staat in `app/build/outputs/apk/debug/app-debug.apk`.

De regressies behandelen onder meer:

- 47 mm referentie op X=3933 mm met afwisselende 3D-rotaties en vrijwel vaste beeldhoeken;
- één afwijkend beeld versus een aanhoudend 70 mm verschoven referentie;
- camerabeweging tussen opname en weergave;
- ontbrekende/stale/herhaalde pakketten;
- eerste kalibratie terwijl het aantal zichtbare, overeenkomende referenties wisselt;
- werkelijk trackingverlies en herkalibratie;
- een referentieconflict dat niet vanzelf verdwijnt wanneer de tags uit beeld gaan;
- de daadwerkelijke plaatsingsbeslissing na al deze toestanden.

`scripts/generate_small_tag_pose_fixture.py` maakt met OpenCV 4.13.0 en seed 914 een onafhankelijke simulatie: 160 beelden van een 47 mm tag op 600 mm cameradiepte, met 0,25 px hoekruis. De maximale lokale posefit is 0,48 px; in 159 beelden wijkt de geschatte rotatie meer dan 1,5° af van de simulatiegrondwaarheid (bereik 0,90–10,13°). Dit is **geen reconstructie van de telefoonopname**. Het laat zien waarom een vaste rotatiegrens op zichzelf onvoldoende is.

Die OpenCV-metingen worden als vaste testdata door de **productiecode** voor fusie en plaatsingsbeslissing gehaald. Met een gekalibreerd anker blijven alle 160 waarnemingen beschikbaar voor plaatsing en blijft het anker staan. De initialisatietest begint zonder anker, verkrijgt binnen 1,4 seconde vanaf de eerste meting een plaatsbare kalibratie en heeft daarna in deze reeks geen uitval meer.

## Resterende grenzen

1. **Toesteltest ontbreekt.** ADB gaf tijdens deze audit geen verbonden toestel. De native Android-tests kunnen daarom nog niet als geslaagd worden gerapporteerd. Er is geen APK op de telefoon geïnstalleerd en er zijn geen telefoonprojecten gewijzigd.
2. **Eén kleine tag bepaalt de hele trafo niet met gegarandeerde precisie.** Een rustig beeld bewijst geen correcte absolute rotatie. Ver uit elkaar liggende, correct ingemeten referenties geven meer geometrische informatie. Een verkeerd gemeenschappelijk nulpunt, printmaat of tagoriëntatie kan niet door filtering worden opgelost.
3. **ARCore-ankerintegratie is nog een architectuurgrens.** De app bewaart momenteel een eigen transform in ARCore-wereldcoördinaten; zij gebruikt geen native `Session.createAnchor()`. ARCore adviseert echte gedeelde ankers om gerelateerde objecten met wereldverfijningen mee te laten bewegen. Een langdurige test met kaartverfijning/herlokalisatie blijft nodig; de korte log bewijst niet dat dit de hier onderzochte blokkering veroorzaakte. Deze audit claimt daarom geen oplossing van alle langdurige ARCore-drift.
4. **Opslag blijft op het gekozen nominale trafovlak.** Het is geen nieuwe meting van willekeurige uitstekende ribben of sensordikte. Eerder opgeslagen sensorposities kunnen uit dit fragment niet geometrisch worden herberekend.

Praktisch verwacht gedrag in de nieuwe build: na de eerste kalibratie blijft de overlay staan en kan plaatsing beschikbaar blijven terwijl een losse posemeting wordt genegeerd. Bij zichtbare correcte tags verschijnt regelmatig `ACCEPT reason=anchor-image-held`. Bij werkelijke blijvende afwijking, referentieconflict of trackingverlies volgt een specifieke melding.

## Vergelijking met andere software

Het gezamenlijke objectframe sluit aan op [OpenCV ArUco boards](https://docs.opencv.org/5.0/tutorials/objdetect/aruco_board_detection/aruco_board_detection.html) en [Vuforia Multi Targets](https://developer.vuforia.com/library/vuforia-engine/images-and-objects/multi-targets/multi-targets/): bekende referenties rondom één object leveren één objectpose. Vuforia vermeldt ook dat de [onderlinge geometrie vast moet blijven](https://developer.vuforia.com/library/vuforia-engine/images-and-objects/multi-targets/recommendations-designing-multi-targets/). De precieze interne filters van commerciële systemen worden daarmee niet gelijkgesteld aan onze implementatie.

Overige primaire bronnen: [OpenCV PnP en meerdere oplossingen](https://docs.opencv.org/4.13.0/d5/d1f/calib3d_solvePnP.html), [ARCore cameraconventies](https://developers.google.com/ar/reference/java/com/google/ar/core/Camera), [ARCore ankers](https://developers.google.com/ar/develop/anchors).
