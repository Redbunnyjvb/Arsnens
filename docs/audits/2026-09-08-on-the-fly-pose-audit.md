# Audit on-the-fly: tagposes, AR-model en sensorplaatsing

Datum: 8 september 2026. Broncode: commit `f4f63cc`, huidige werkmap. Alleen audit; geen functionele codewijzigingen. De al bestaande wijziging in `.idea/misc.xml` is ongemoeid gelaten.

**Conclusie:** meerdere correct vastgelegde en correct geplakte referentietags kunnen samen een bruikbare modelpose leveren. De huidige implementatie garandeert echter niet dat bij drie tags de twee correcte tags een verschoven derde uitsluiten. Bovendien gebruiken live plaatsing en vaste overlays op een belangrijk pad verschillende poses. Daarmee is de on-the-fly-keten nog niet voldoende robuust om juiste meetposities af te leiden uit alleen een rustig, groen AR-beeld.

Dit is een audit van de volledige softwareketen en de bestaande tests, geen praktijktest met een telefoon en fysieke tags. De actieve instelling en fysieke tagposities op het toestel zijn niet uitgelezen.

## Antwoord op het scenario met drie tags

| Situatie | Huidige werking | Gevolg |
|---|---|---|
| Drie tags kloppen, standaardinstelling | `NearestTag` selecteert één tag op leesbaarheid en eigen reprojection error, met hysterese bij wisselen. | Meer zichtbare tags geven keuze en dekking; normaal geen gezamenlijke pose uit drie tags. Meetruis kan bij een tagwissel nog een correctie geven. |
| Drie tags kloppen, Multi-tag ingesteld | Alle twaalf hoeken gaan samen naar `solvePnP`. Bij gemiddelde hoekfout ≤ 3 px wordt de gezamenlijke pose gebruikt. | Kan de pose beter bepalen, afhankelijk van spreiding, beeldkwaliteit en correcte geometrie. Bij afwijzen volgt terugval naar single-tag of het latere consensuspad. |
| Eén tag verschoven, standaardinstelling | De gekozen tag wordt niet eerst tegen de andere tags gecontroleerd. | Is de verschoven tag de gekozen referentie, dan kan het hele model naar die foutieve referentie toe corrigeren. Is een correcte tag gekozen, dan hoeft de verschoven tag het model niet direct te beïnvloeden. |
| Eén tag licht verschoven, Multi-tag ingesteld | De gezamenlijke oplossing wordt geaccepteerd zolang de gemiddelde hoekfout ≤ 3 px blijft. | Een verschoven/gedraaide compromispose kan slagen. Er is geen voorafgaande uitsluiting per tag op dit pad. |
| Eén tag duidelijk verschoven, gezamenlijke fit > 3 px | Een goede lokale tagpose wordt direct geretourneerd. | Er volgt niet automatisch een oplossing op alleen de twee correcte tags. Ook de verschoven tag kan de lokale winnaar zijn. |
| Tags verdwijnen | Een bestaand anker wordt met ARCore gevolgd; na 10 seconden zonder geaccepteerde tagkalibratie verschijnt driftstatus. | Tracking bewaart continuïteit maar bewijst geen absolute uitlijning met het object. |

Met “kloppen” wordt bedoeld: juiste unieke ID en dictionary, gemeten zwarte tagmaat, centrumpositie, oriëntatie en positie ten opzichte van hetzelfde modelcoördinatenstelsel. Alleen een correct centrum is niet voldoende als maat, draairichting of STL-schaal/offset onjuist is. Een gemeenschappelijke fout in alle tags is met onderlinge overeenstemming alleen niet aantoonbaar.

## Gecontroleerde keten

1. **Invoer en opslag:** operatorcoördinaten worden naar het canonieke boxframe omgezet: X naar rechts, Y naar achteren, Z omhoog; millimeters. Tagopslag gebruikt het tagcentrum, vlakrotatie en eventuele buitenwaartse offset. Nieuwe tags kunnen via de bestaande pose en een straal naar het gekozen boxvlak worden ingemeten. Dat is een afgeleide meting vanuit het bestaande anker, geen onafhankelijke controle daarvan. Zie `CoordinateFrameMapper.kt`, `TagPlacement.kt`, `WorkflowAppState.kt:1306` en `:1380` e.v.
2. **Hoekgeometrie en detectie:** de vier modelhoeken volgen centrum ± halve tagmaat en opgeslagen Eulerrotatie (`TransformerGeometry.kt:166`). De detector normaliseert de hoekvolgorde met een verschuiving van twee hoeken (`AprilTagDetector.kt:239`). De fysieke printconventie achter die correctie is niet met een echte print getest in deze audit.
3. **Camerakalibratie:** CPU-camerabeeld en `frame.camera.imageIntrinsics` worden samen vastgelegd (`ArCoreCameraPanel.kt:475`). ARCore-translaties worden van meters naar millimeters omgezet (`:321`). De CV/GL-assenconversie is expliciet in `Transform3D.kt:187` e.v.
4. **Pose:** individuele IPPE-oplossingen, temporele keuze bij ambiguïteit, SQPnP-fallback, lokale selectie en optionele gezamenlijke solve (`AprilTagDetector.kt:265` e.v.). Alleen bekende actieve referentiemarkers tellen mee; een gedetecteerde sensor-tag wordt door detectie alleen geen kalibratiereferentie.
5. **ARCore-fusie:** kandidaatanker = ARCore-camerapose bij opname × CV/GL-conversie × tagpose. Rendering gebruikt de huidige camera ten opzichte van dat anker (`ArCoreCameraPanel.kt:716`, `:746`, `:951`). Dit compenseert camerabeweging tijdens detectie voor de gefuseerde modelpose.
6. **Model en bestaande sensoren:** STL-geometrie krijgt schaal, rotatie en offset via `stlModelTransform`; vaste overlays volgen primair de gefuseerde pose (`WorkflowArOverlays.kt:1164`, `:1385`). Een eigen `referenceTagId` maakt de sensor in de normale ARCore-route dus niet immuun voor globale ankerfouten.
7. **Nieuwe sensor en audit:** cursorvlak, eventuele sensor-tagcentrumpositie, referentie-ID en kwaliteitsgegevens worden opgeslagen (`WorkflowAppState.kt:2311`). Optionele straal-replay kan opgeslagen sensorposities later wijzigen; dit staat standaard uit (`AppSettings.kt:57`).

De basale eenheden, matrixrichting en canonieke vlakgeometrie zijn op de gecontroleerde paden coherent. Dat neemt onderstaande inconsistenties in selectie, tijd en plaatsing niet weg.

## Bevindingen op prioriteit

### 1. P1 — Consensuscontrole wordt overgeslagen bij een goede lokale fit

**Bewijs:** `AprilTagDetector.kt:356` probeert in Multi-tag eerst alle tags. `:375` retourneert vervolgens een lokale pose zodra diens fout ≤ 3 px is. `solveConsensusAprilTagPose` wordt pas aangeroepen op `:398`, nadat die vroege return niet is genomen. In NearestTag is die vroege lokale return het normale pad. De gezamenlijke solve op `:701` gebruikt gewone `solvePnP`, zonder robuuste uitsluiting van tags. Acceptatie gebruikt de gemiddelde Euclidische hoekfout (`:1245`), geen maximum per tag.

**Impact:** een fysiek verschoven tag kan zijn eigen vier hoeken uitstekend verklaren met een andere camera/modeltransformatie. Lage lokale reprojection error ontdekt die fout niet. Twee andere, juiste tags hoeven daardoor niet te winnen. De bestaande consensusfunctie met minimaal twee inliers en een drempel van 5 px is wel aanwezig, maar staat te laat in de selectie.

**Aanpak:** bij meerdere zichtbare referenties eerst overeenstemming tussen volledige tags toetsen; kandidaatposes tegen elke tag evalueren, een betrouwbare groep selecteren en daarop opnieuw oplossen. Gebruik ook een fout per tag en zichtbare registratie van uitgesloten IDs. Bij twee strijdige tags is zonder extra referentie/prior niet zomaar vast te stellen welke klopt. Bij drie tags is een consistente groep van twee bruikbaar onder de aanname dat de meerderheid correct is.

### 2. P1 — Cursor, opgeslagen straal en overlay gebruiken verschillende poses

**Bewijs:** `WorkflowAppState.kt:803` vervangt tijdens een verse per-tagpose de hoofdpose door de individuele pose en zet `displayProjection = null` voor de cursor. `WorkflowArOverlays.kt:1385` projecteert bestaande sensoren juist eerst via de gefuseerde pose; het STL-model doet hetzelfde op `:1183` e.v. `storePlacementRayFor` op `WorkflowAppState.kt:911` bewaart een straal uit de oorspronkelijke `aprilTagResult`, dus bij ARCore-tracking uit de gefuseerde projectie.

**Impact:** tijdens demping, een tagwissel of een nog niet geaccepteerde correctie kan de cursorpositie afwijken van de positie die na opslaan op het scherm verschijnt. De straal voor latere driftcorrectie hoeft dan evenmin dezelfde straal te zijn die de plaatsing bepaalde. De per-tag cameratransformatie wordt daarnaast tot 250 ms vastgehouden; de huidige camerabeweging wordt op dat cursorpad niet via de huidige ARCore-pose verwerkt.

**Aanpak:** bepaal één expliciete pose/tijdreferentie voor cursor, hitpunt, opgeslagen positie, straal-replay en overlay. Als plaatsing bewust op een individuele detectie gebeurt, transformeer die detectie naar de huidige camera en bewaar exact de werkelijk gebruikte straal en het werkelijk gebruikte oppervlak.

### 3. P1 — Automatische STL-snap overschrijft de opgegeven tagdiepte

**Bewijs:** na opslaan in on-the-fly met een zichtbaar STL wordt automatisch `pendingTagSnapId` gezet (`WorkflowAppState.kt:1541`); `WorkflowTagScreens.kt:154` voert de snap uit. `snapTagToModelSurface` schiet vijf stralen door de zichtbare modellen en vervangt de normaalcoördinaat direct door het buitenste raakpunt (`WorkflowAppState.kt:2837`). De eerder ingevoerde buitenwaartse offset wordt daar niet opnieuw toegepast.

**Impact:** een juist gemeten afstand door een afstandhouder, tagdikte of bewuste offset kan verdwijnen. Voorbeeld: voorvlak Y=0, ingevoerde buitenwaartse offset 10 mm geeft Y=-10; een STL-wand op Y=0 maakt daar automatisch weer Y=0 van. Ook een andere zichtbare uitstekende STL-component kan het gekozen oppervlak bepalen. De tagoriëntatie blijft de gekozen boxvlakrotatie, ook bij een schuin STL-oppervlak.

**Aanpak:** scheid oppervlakbasis, fysieke tagoffset en gemeten centrum expliciet. Laat een automatische modelsnap geen autoritatieve meting vervangen. Test vlakke, uitstekende en schuine oppervlakken afzonderlijk.

### 4. P2 — Sensorplaatsing gebruikt niet consequent hetzelfde oppervlak

**Bewijs:** de gewone cursor snijdt vlakken door referentietags in `estimateCursorOnReferenceSurface` (`AprilTagDetector.kt:994`); dit is geen snede met de STL-driehoeken. Bij automatisch koppelen van een sensor-tag wordt de cursor vervangen door `estimateSurfaceAtPixel` (`WorkflowAppState.kt:2334`), die juist de nominale box snijdt (`AprilTagDetector.kt:904`). De ARCore-depthcursor staat uit (`ArCoreCameraPanel.kt:1508`).

**Impact:** een sensor op een rib of wand die niet op de boxdiepte ligt kan een andere positie krijgen met en zonder automatisch tagkoppelen. Een STL-model dat er goed uitziet betekent niet dat het opgeslagen punt werkelijk op zijn oppervlak ligt. Tagvlakken buiten de box kunnen bovendien worden afgewezen door de boxgrenzen van de sensorplaatsing.

**Aanpak:** leg vast welk oppervlak de plaatsing bedoelt en gebruik dat consequent voor losse cursor, sensor-tagcentrum en replay. Voor plaatsing op echte STL-geometrie is een overeenkomstige mesh-raycast nodig.

### 5. P2 — Kwaliteitsweergave detecteert geen foutieve tagkaart

**Bewijs:** `PlacementQuality.kt:85` bepaalt groen op trackingstatus, fit, jitter en camerabeweging. Onderlinge tagresiduen ontbreken; `isStableLock` is geen voorwaarde voor groen. `saveSensorAtCursor` registreert de kwaliteit maar vereist geen groene kwaliteit of stabiele lock. De mm-conversie (`PlacementQuality.kt:135`) gebruikt de norm van `TransformerPose.translationMm`: dit is de projectoorsprong in cameracoördinaten, niet de positie/diepte van de referentietag.

**Impact:** een stabiele maar verkeerd geplaatste tag kan groen opleveren. Het getoonde fit-getal in mm is ook geen absolute plaatsingsnauwkeurigheid en hangt in de huidige berekening onterecht af van de afstand tot de projectoorsprong. Bijvoorbeeld: oorsprong op camera-(3000,0,2000), gebruikte tag op camera-(0,0,2000): de gebruikte afstand is circa 3606 mm in plaats van tagdiepte 2000 mm.

**Aanpak:** voeg toestand en reden voor tegenstrijdige referenties toe. Gebruik voor een lokale px→mm-indicatie de relevante cameradiepte en benoem die expliciet als fit-indicatie. Neem een eventuele waarschuwing/bevestiging bij onbetrouwbare plaatsing als aparte productkeuze.

### 6. P2 — Detectieleeftijd begint bij verwerking, niet bij opname

**Bewijs:** `ArCoreCameraPanel.kt:729` zet `lastDetectionsMillis = now` bij ontvangst in de fusie. `detectionAgeMillis` op `:987` rekent vanaf dat moment. De opnamepose wordt wel meegenomen voor de ankerberekening, maar de detectietijd zelf telt niet mee in de gerapporteerde leeftijd.

**Impact:** een traag verwerkte detectie wordt bij aankomst als 0 ms oud behandeld. Vooral het individuele cursorpad uit bevinding 2 kan daarmee werken op een ouder camerabeeld dan de versheidsindicator aangeeft. Het signaal voor beweging tijdens detectie verzacht dit in de kwaliteitsweergave, maar verplaatst de cursor niet naar het huidige cameraframe.

**Aanpak:** geef monotone opnametijd en detectiesequentie mee tot aan de plaatsing; bereken versheid vanaf opname en gebruik afzonderlijke detecties voor bevestiging.

## Wat doet de fusie met een verschoven tag?

Een kleine consistente fout wordt niet weggefilterd door smoothing. In een project met meerdere opgeslagen tags mag een geselecteerde single-tag na drie detecties dezelfde ID correcties binnen 250 mm en 10° leveren; de blendfactor is 0,25. Dat dempt de overgang, maar beweegt uiteindelijk naar de kandidaat. Grotere single-tagverschillen kunnen na drie ruimtelijk consistente kandidaten opnieuw ankeren. Deze bevestiging controleert herhaling, geen meerderheid van correcte tags.

Een geaccepteerde multi-tagkandidaat mag bij een bestaand normaal anker tot 2500 mm en 35° verschillen en gebruikt in het normale geaccepteerde bereik factor 0,85 (`ArCoreCameraPanel.kt:760`, `:816`, `:1092`, `:1511`). Bij initialisatie, verlopen kalibratie of herkalibratie gelden vervangingspaden. Deze limieten zijn continuïteitsregels, geen bewijs dat een fysieke tag correct ligt.

## Rekenvoorbeeld: 10 mm verschoven

Neem ter illustratie een frontaal vlak op 2000 mm diepte en een brandpuntsafstand van 1000 px. Een zijwaartse verplaatsing van 10 mm geeft 5 px beeldverschuiving. Een single-tagpose kan die volledig opnemen als 10 mm translatie en dan nog 0 px hoekfout hebben. Het model kan dus meeschuiven terwijl de tag perfect wordt gevolgd. Dit volgt direct uit de perspectiefprojectie.

In een vereenvoudigd model waarin alleen translatie wordt geschat, leveren twee correcte tags en één tag op +10 mm een least-squarescompromis van +3,333 mm. De gemiddelde absolute hoekfout is dan 2,222 px: onder de huidige drempel van 3 px. Beide berekeningen zijn met een klein onafhankelijk rekenvoorbeeld gecontroleerd. **Dit is geen uitvoering van de echte zes-vrijheidsgraden-PnP-solver:** de echte uitkomst hangt ook af van rotatie, tagspreiding, diepte en kijkhoek. Er is geen algemene grens “tot X mm verschoven is veilig”.

## Verificatie en resterende praktijktest

`gradlew.bat testDebugUnitTest --rerun --offline` is succesvol uitgevoerd nadat een ontbrekende buildafhankelijkheid was opgehaald. **119 tests, 20 suites, 0 failures, 0 errors, 0 skipped.** De testtaak is werkelijk opnieuw uitgevoerd, niet alleen uit cache overgenomen.

De aanwezige tests controleren onder meer vlak- en hoekgeometrie, coördinatenmapping, AR-projectie, smoothing, STL-raycasts en kwaliteitsberekening. Er zijn geen bestaande tests gevonden die de volledige detectie→multi-tagselectie→ARCore-fusie→cursor→opslagroute met één verschoven tag uitvoeren. Groene unit-tests weerleggen bovenstaande bevindingen daarom niet. Native OpenCV-detectie en fysiek toestelgedrag zijn niet getest.

Aanbevolen acceptatiescenario's voor de verbeterde keten:

- Drie exact bekende tags: vergelijk single-tag, alle tags en iedere combinatie van twee tegen dezelfde onafhankelijke modelreferentie.
- Verplaats tag C met 5, 10 en 20 mm, zowel in het vlak als in diepte; varieer ook rotatie. Maak C afwisselend de best zichtbare tag. Verwacht stabiele keuze van A+B of expliciete onvoldoende-consensusmelding.
- Beweeg de camera tijdens detectie, wissel tussen tags en maak tijdelijk geen tag zichtbaar. Sla een sensor op vóór, tijdens en na ankeracceptatie; cursor, overlay en bewaarde straal moeten overeenkomen.
- Herhaal met STL-rib, buitenwaartse tagoffset en sensor-tagkoppeling aan/uit. Controleer het opgeslagen XYZ ten opzichte van het bedoelde oppervlak.
- Controleer echte tagprints op alle vijf vlakken, juiste zwarte maat, printoriëntatie en de gebruikte dictionary.

De eerste implementatieprioriteit is consensus vóór lokale selectie, gevolgd door één consistente pose en oppervlak voor de hele plaatsingsketen. Daarna de automatische dieptewijziging en kwaliteits/versheidsinformatie corrigeren.

## Primaire documentatie

OpenCV beschrijft `solvePnP` als bepaling van de object→camera-transformatie uit 3D/2D-correspondenties en onderscheidt daarvan RANSAC voor uitschieters: [OpenCV PnP](https://docs.opencv.org/4.12.0/d5/d1f/calib3d_solvePnP.html). Dit is achtergrond voor de geometrische afleiding; het auditbewijs voor het concrete selectiegedrag staat in de lokale broncode.

ARCore documenteert `getImageIntrinsics` voor het ongeroteerde CPU-camerabeeld: [ARCore Camera](https://developers.google.com/ar/reference/java/com/google/ar/core/Camera). De lokale code gebruikt die gegevens bij het camera-imagepad.
