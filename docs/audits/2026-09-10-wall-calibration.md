# Wanden en bovenkant scannen — 10 september 2026

## Bedoeling en keuzes

Toegevoegd aan dezelfde ARsens-app, als projecteigenschap naast **Bekende tagposities**. Oude projecten blijven die bestaande methode gebruiken. De back-up ARsensv1 is niet aangepast. Er is geen tweede AR-engine en er is niet gepusht.

De nieuwe methode gaat uit van een rechtopstaande rechthoekige tank. Tags moeten vlak op de gekozen wand liggen. Hun afzonderlijke X/Y/Z-posities hoeven niet vooraf bekend te zijn. De scan lost één gezamenlijk, orthogonaal tankframe op; hij snijdt geen willekeurig scheve, onafhankelijk geschatte vlakken.

| Keuze binnen Wanden scannen | Benodigde vlakken | Afmetingen |
| --- | --- | --- |
| Afmetingen bekend | Twee aangrenzende zijwanden en bovenkant | Ingevoerde lengte, breedte en hoogte blijven exact behouden |
| Afmetingen bepalen | Alle vier zijwanden en bovenkant | Afstand tussen tegenoverliggende wanden bepaalt lengte/breedte; boventags bepalen bovenvlak |

Per zijwand zijn minstens twee verspreide tags nodig. De eerste boventag koppelt de bovenkant; daarna kunnen extra boventags willekeurig op hetzelfde vlak worden toegevoegd. Meer tags bieden extra mogelijkheden om een afwijkende meting uit te sluiten. De **dekseltag bepaalt de bovenkant**. Een tag of meting onder de tank is niet nodig. Bij bekende afmetingen loopt de box vanaf het deksel de ingevoerde projecthoogte omlaag. Bij **Afmetingen bepalen** worden lengte en breedte gescand; de gewenste zijwandhoogte onder het deksel wordt ingevuld. Alleen verticale wanden en een bovenvlak bevatten geen informatie over de onderrand. De oudere gegevensvorm met een zijtag op een bekende hoogte blijft leesbaar en rekenkundig ondersteund, maar wordt niet meer als verplichte stap getoond.

Een vlak verhoogd deksel kan met een expliciete verhoging boven de tankrand worden gebruikt. Dan begint de bovenkant van de box die afstand onder het tagvlak; de opgeslagen boventags houden hun echte, hogere positie. Standaard is dit 0 mm. Een schuin deksel is geen ondersteund bovenvlak. Voorbereide en gemeten sensorposities, namen, IDs, radius, log en projectcoördinaten worden niet aangepast door kalibratie.

## Naar boven lopen en de contour onthouden

1. Kies **Referentiemethode → Wanden scannen** en de bron van de afmetingen.
2. Kies **Voor / Achter / Links / Rechts** en druk op **Tag N op … vastleggen**. De tagmaat is direct bereikbaar; hoogte-instellingen staan onder **Deksel / hoogte**. Bij onbekende maten vul je daar de gewenste zijwandhoogte onder het deksel in.
3. Kies **Grondcontour berekenen**. Alleen de zijmetingen bepalen deze 2D-contour. Bij onbekende maten blijft de hoogte hier nog onbekend; er wordt geen hoogte verzonnen.
4. Kies **Verder: bovenkant koppelen**. Een zijtag bij de bovenrand en een boventag om de hoek mogen samen zichtbaar zijn, maar achtereenvolgens zien mag ook. ARCore verbindt hun camerastanden in hetzelfde tankframe. Er is **geen verplichte gelijktijdige hoekscan en geen extra tijdslimiet tussen zij- en bovenscan**.
5. Neem de eerste boventag op en kies **Bovenkant koppelen**. De zijmetingen van de goedgekeurde grondcontour blijven vast; de boventag bepaalt de verticale ligging van het deksel. De voorlopige hoogte van de 2D-dwarsdoorsnede wordt nu vervangen door de definitieve boxhoogte; de X/Y-contour en richting blijven hetzelfde.
6. Met **Extra boventags opnemen** kunnen verdere tags vrij op hetzelfde vlak worden geleerd. Controleer de volledige contour en kies **Kalibratie gebruiken** om alle referenties permanent op te slaan.

Het vlak wordt door de gebruiker gekozen. De gemeten tagrotatie controleert of de tag bij een verticale zijwand of horizontaal deksel past; deze rotatie kiest niet zelf tussen Voor/Achter/Links/Rechts. Ook **Boven** is direct vanaf het begin bereikbaar. Extra tags mogen willekeurig op hetzelfde vlak liggen, mits hun metingen bij de gekozen contour passen.

Kort na elkaar kijken is geen garantie tegen drift. De AR-contour kan met **Contour aan/uit** worden geschakeld; **Hele beeld** verbergt het bedienpaneel. Een gezamenlijk camerabeeld kan als aanvullende diagnostiek worden vastgelegd, maar blokkeert koppelen of opslaan niet als het ontbreekt.

Alle tijdelijke waarnemingen staan in één gedeeld native ankerframe. De camera, dat anker en de projectie worden uit hetzelfde renderframe genomen. Detecties blijven gekoppeld aan de camera bij opname. Een gepauzeerd/vervangen anker of verloren cameratracking maakt de lopende scan ongeldig; losse werelden worden niet samengevoegd. Bij opnieuw beginnen krijgt de scan een nieuwe sessie-ID. Oude pakketten tellen niet mee.

Na goedkeuring blijven uitsluitend permanente projectgeometrie en kalibratiemetadata bewaard. De tijdelijke wereldpose wordt niet opgeslagen. Bij heropenen herkent de bestaande AR/fusionketen de geleerde referentietags en lijnt dezelfde contour opnieuw uit. Een onbekende tag kan buiten de actieve wandscan geen projectpose opleveren. Annuleren of een mislukte herscan behoudt de eerder opgeslagen geometrie.

Deze keuze volgt de ARCore-documentatie: numerieke wereldposes kunnen tussen frames veranderen, terwijl een gedeeld anker lokale relaties bewaart. Zie [ARCore Pose](https://developers.google.com/ar/reference/java/com/google/ar/core/Pose) en [Working with anchors](https://developers.google.com/ar/develop/anchors).

## Keten en grenzen

- `StandaloneTagPoseSolver` hergebruikt de bestaande native OpenCV/IPPE-poseberekening in een lokaal tagframe. Sensor-tag-ID's en dubbele ID's worden niet geleerd als wandreferentie.
- `ArCoreCameraPanel` maakt eerst een tijdelijk gedeeld anker en verzamelt pas daarna tagmetingen in dat ankerframe. De normale fusionroute wordt tijdens de scan niet gebruikt voor sensorplaatsing. Na acceptatie worden gewone `Marker`-objecten aan precies die bestaande route aangeboden.
- `WallCalibrationSession` bewaart maximaal 40 samples per tag. Voor een schatting zijn minstens acht metingen over 700 ms nodig, minimaal 60 ms uit elkaar. Versheid maximaal 250 ms, kortste beeldrand minimaal 40 px, reprojection error maximaal 1,5 px. De robuuste inliers moeten minstens 70% van de samples zijn; spreiding maximaal 10 mm.
- `WallCalibrationSolver` fixeert Z met zwaartekracht, schat de horizontale richting uit tagmiddens en normaalrichtingen en gebruikt robuuste wandafstanden. Uitschieters worden uitgesloten; vereiste vlakken mogen daarna niet onvoldoende tags overhouden. Tags moeten horizontaal voldoende verspreid staan. De bovenkant bepaalt de verticale ligging van het deksel; de ingevoerde hoogte bepaalt de onderrand van de box. De gebruiker kan eerst een grondcontour bekijken en daarna één of meer boventags leren; de opgeslagen zijmetingen worden daarbij hergebruikt.
- De preview bewaart de bijbehorende datum, bronkeuze en tagtoewijzingen als één snapshot. Acceptatie vereist nog actuele tracking in hetzelfde frame. De metadata bevat een geometriesignatuur: later gewijzigde tagmaat/positie/rotatie of afmetingen mogen niet stilzwijgend als dezelfde kalibratie gelden.
- Zonder bekende afmetingen begint een nieuw project met onbekende maten (0). 2D/3D en fysieke plaatsing worden pas beschikbaar zodra bruikbare maten aanwezig zijn. STL-import overschrijft de maten bij deze methode niet.
- Het getoonde RMS/max/normaalverschil beschrijft de **onderlinge overeenkomst van de metingen**, niet de werkelijke meetnauwkeurigheid.

## Gevonden tijdens verificatie

De scan-UI filterde de opnameknoppen op de normaalrichting van een tag. Een platte tag met **Voor** geselecteerd kreeg daardoor wel het label “kies wand”, maar geen knop. De nieuwe regressietest faalde aantoonbaar op deze oude UI (`wall-capture-before-test.txt`). De opnameactie staat nu vast onderin en noemt zowel de tag als het geselecteerde vlak. Bij een afwijkende oriëntatie verschijnt een concrete aanwijzing; de knop wordt niet verborgen. Een verkeerde toewijzing kan door een andere vlakkeuze en opnieuw vastleggen worden vervangen.

De onderreferentie is vervangen door een dekselreferentie. Tagmaat, hoogte en het overzicht staan achter compacte knoppen. Er zijn geen flits-, autofocus- of actief-paneelknoppen in deze scanroute. De AR-preview toont eerst de 2D-doorsnede en daarna de gehele box.

De Android-opslagtest faalde eerst doordat Android JSONObject `-0.0` als `0` schrijft. Dat veranderde de geometriesignatuur na heropenen. De gegenereerde Eulerhoeken en de signatuur normaliseren nul nu. De oorspronkelijke fout staat in `app/build/wall-state-pose-before.txt`; de heropen-regressie slaagde vervolgens.

De tests controleren ook rol van de gedrukte tag (inclusief ±90° en 180°), verschillende wanden, een verschoven extra tag, onvoldoende spreiding, ontbrekende bovenkant, een verhoogd/schuin bovenvlak, losse zij/bovenscans, gemengde camerasnapshots, herhaalde/verouderde pakketten en verlies van tracking.

## Gewijzigde bestanden

- `data/Models.kt`, `WallCalibrationData.kt`, `WallCalibrationJson.kt`, `JsonProjectStore.kt`, `LocalProjectRepository.kt`: optionele projectmethode, dimensiebron en opslag.
- `ar/calibration/WallCalibrationModels.kt`, `WallCalibrationSession.kt`, `WallCalibrationSolver.kt`, `StandaloneTagPoseSolver.kt`: tijdelijke metingen, grondcontour en boventaggeometrie.
- `ar/AprilTagDetector.kt`, `ArCoreCameraPanel.kt`: afgeschermde scanroute en opnamekoppeling.
- `ui/WorkflowAppState.kt`, `WorkflowWallCalibration.kt`, `ArSensHomeScreens.kt`, `WorkflowTagScreens.kt`: projectkeuze, scanbediening, preview en acceptatie.
- `WallCalibrationTest.kt`, `WallTestFixture.kt`, `WallWorkflowStateTest.kt`, `WallPosePipelineTest.kt`, `WallCalibrationUiTest.kt`: reken-, opslag-, native-keten- en schermtests.

## Verificatie en resterende praktijktest

Rekentests: **213 geslaagd, 0 fouten**. Debug-app en Android-test-APK bouwen offline. Zie `app/build/wall-capture-final-build.txt`. Android-integratie- en schermtests: **37 unieke tests geslaagd** op emulator API 37 in één volledige run (`app/build/wall-calibration-verified-android-tests.txt`):

- 5 wandscan-UI-tests, waaronder de opnameknop bij een horizontale tag met Voor geselecteerd, directe keuze Boven, bereikbare hoogte-instellingen en volledige AR-preview.
- 10 wandscan-state-tests, waaronder herindelen van een verkeerd gekozen vlak, ongeldig maken van een oude zijcontour, opeenvolgend koppelen zonder overlap, eerste boventag plus extra tags en behoud van project/sensor/loggegevens bij opslag en heropenen.
- 2 native wandpose-tests: lokale onbekende tagposes via OpenCV, leren van permanente markers, vervolgens opnieuw uitlijnen via de bestaande fusionketen met zowel een zijtag als uitsluitend een boventag.
- 20 bestaande camera-, sensor-, native-pose- en 2D-tests.

De concrete opnameknopregressie faalde eerst op de oude UI (`app/build/wall-capture-before-test.txt`, één test, één fout), en slaagde daarna in de volledige bovenstaande run. De nieuwe rekentests bevestigen ook dat zonder onderreferentietag het deksel de verticale ligging bepaalt, en de eerder berekende X/Y-contour en richting behouden blijven. Schermen zijn als gerenderde Compose-beelden geïnspecteerd; de vijf vlakknoppen breken niet meer midden in een woord af.

Alle camera- en world-pose-invoer in deze integratietests is gecontroleerde testinvoer. Native OpenCV draait werkelijk op Android; de echte ARCore-camera en fysieke nauwkeurigheid zijn niet op een toestel gemeten.

Een emulatorrun is door Android wegens `LOW_MEMORY` gestopt (bevestigd via `dumpsys activity exit-info`); ook de emulator-System-UI had eerder een ANR. De testemulator is daarom zonder data te wissen met 4 GB RAM herstart. Afgebroken runs tellen niet als geslaagde tests. De System UI van deze preview-emulator bleef incidenteel een ANR tonen; voor visuele inspectie worden daarom ook de gerenderde Compose-appschermen rechtstreeks vastgelegd, los van de emulator-shell.

Op een echte telefoon nog controleren:

1. Zwarte tagmaat controleren; bekende tankmaten en de gewenste zijwandhoogte onafhankelijk nameten. Met een platliggende tag bij Voor moet de opnameknop zichtbaar zijn en Boven als passende keuze worden genoemd.
2. Dezelfde tank met beide dimensieopties scannen. Contour op alle zijden én boven vergelijken met fysieke randen; afwijkingen in mm vastleggen.
3. Bij een hoek tags samen én achtereenvolgens zien, daarna uitsluitend boven verder scannen. Beide routes moeten kunnen koppelen en extra boventags toevoegen. Een bewust onderbroken trackingreferentie moet acceptatie blokkeren.
4. Eén tag verschuiven of verkeerd aan een vlak koppelen. Controleer uitsluiting/weigering en de gebruikte tag-ID's.
5. Afsluiten/heropenen, uitsluitend boventags scannen en controleren of de eerder opgeslagen contour terugkomt. Daarna een voorbereide sensor op een andere plaats vastleggen; doelradius en historisch log moeten behouden blijven.
6. Een vlak verhoogd deksel met een gemeten offset testen. Een schuin deksel niet als horizontale tankbovenkant gebruiken.

Er is geen geslaagde fysieke AR-toesteltest of gegarandeerde nauwkeurigheid geclaimd.
