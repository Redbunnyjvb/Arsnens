# Zichtbare referentie afgewezen en camerabediening

Vervolgaudit naar aanleiding van de vijf toestelbeelden van 9 september 2026. Basis: `3c1022d`. De foto's zijn waarnemingen; ze bewijzen geen fysieke tagpositie, camerakalibratie of gemeten AR-nauwkeurigheid.

## Wat de foto's wel aantonen

Tag 2 wordt vers gedetecteerd: 33–67 ms meetleeftijd. De afwijzingen zijn `REJECT/reference-jump`, met respectievelijk 24 mm/2,7°, 18 mm/5,5° en 12 mm/7,1°, telkens samples 0. Een ander beeld meldt `ACCEPT/anchor-image-held` bij 1 mm/1,6°.

De detectie staat dus aan. De sprongcontrole blokkeert de meting vóór de bevestigingsmetingen beginnen. De grens voor één referentie is 30 mm óf 1,5°; de getoonde afwijzingen overschrijden de hoekgrens. Langer dezelfde tag zien levert op deze route geen extra bewijs op. Dat verklaart de melding ondanks een zichtbare blauwe contour. Paars blijft de bestaande gefuseerde uitlijning tonen.

Een enkele vlakke tag heeft een kwetsbare schatting van de normaal, vooral bij een klein beeld of bijna frontaal aanzicht. IPPE geeft twee mogelijke poses; de detector gebruikt al een naar het opnameframe omgerekende voorspelling om te kiezen. Een kleine pixelafwijking kan toch een duidelijk andere 3D-oriëntatie toelaten. Zie de [IPPE-auteurs](https://github.com/tobycollins/IPPE) en [OpenCV solvePnP](https://docs.opencv.org/4.12.0/d5/d1f/calib3d_solvePnP.html). Of dit precies de hoekafwijking op deze telefoon veroorzaakt, is nog niet gemeten. Ook een onjuiste eerste oriëntatie, fysieke tagrotatie, onvlak papier of onjuiste maat kan niet uit de foto's worden uitgesloten.

## Gecontroleerde keten

1. `AprilTagDetector`: vier hoekpunten, fysieke tagmaat, referentie-selectie, meerdere poses, consensus en uitsluiting. Eén sensor-tag vervangt geen referentietag.
2. `ArCoreCameraPanel`: detectiepakket blijft gekoppeld aan eigen camera, tijd, volgnummer en trackingFrameId. De voorspelling wordt naar de opnamecamera omgerekend.
3. `ArCoreAprilTagFusion` → `AnchorPoseFilter`: geometrische controles, bevestigingsvenster, geleidelijke correctie. Dit is de bewezen blokkade voor kleine positieverschillen met een onzekere taghoek.
4. Eén native anker blijft de basis voor project, model, sensoren en cursor. Automatische correctie wijzigt de projecttransform binnen dat anker. PAUSED/STOPPED gebruiken de bestaande herstelroute en geen gepauzeerde ankerpose. Oude frame-ID's worden afgewezen.
5. Overlays gebruiken de actuele camera; de blauwe gemeten contour wordt vanuit het opnameframe naar het actuele beeld gebracht. Plaatsing gebruikt de gefuseerde projectcoördinaten en actuele kwaliteitscontrole. Voorbereide doelpositie en echte meting blijven apart.
6. `recalibrateAr()` doet meer dan normale correctie: het wist tijdelijke pose-, cursor-, lock- en straalgegevens en verhoogt calibrationRevision. De gewijzigde marker-signatuur reset vervolgens fusion en de native referentie; oude detectiepakketten worden afgewezen. De kortlevende interne PnP-voorkeur van de detector wordt niet afzonderlijk gewist bij alleen een ijkverzoek. Er volgt een nieuwe eerste kalibratie. Dat verklaart waarom expliciet ijken kan helpen waar gewone correctie wordt tegengehouden. Bestaande projectcoördinaten worden daarbij niet herschreven.

## Gerichte posereparatie

`SingleReferenceTranslation.kt` past alleen de drie translatiecomponenten aan, met behoud van de bestaande oriëntatie. De oplossing gebruikt alle vier de hoekpunten en de camera-intrinsics; het referentiemiddelpunt wordt gebruikt om grote projectcoördinaten uit de lineaire oplossing te houden.

Deze route is alleen beschikbaar voor een bestaand anker, buiten relocalisatie, met één verse, actieve en niet uitgesloten referentie. Grenzen:

- Zowel de ruwe als de berekende verandering van het referentiemiddelpunt maximaal 30 mm.
- Ruwe hoek maximaal 12°; deze hoek wordt **niet** overgenomen. De bestaande filtergrens voor rotatie blijft 1,5°.
- Kortste tagrand minstens 40 beeldpixels.
- Zowel de ruwe pose als de positie-oplossing moet elk hoekpunt binnen de bestaande 1,5 px consistentiegrens verklaren; dit is geen nauwkeurigheidsclaim.
- Ruwe en aangepaste referentiepositie liggen maximaal 8 mm uit elkaar; ongeldige, onvolledige en niet eindige gegevens worden afgewezen.
- De kandidaat gaat vervolgens door het bestaande bevestigingsvenster. Herhaalde/stale pakketten, tegenstrijdige tagsets en uitgesloten referenties leveren geen bewijs.

Zo wordt een betrouwbare kleine positiecorrectie niet meer volledig geblokkeerd door een onzekere normaal. Grote verplaatsingen of duidelijk waarneembare rotaties blijven een tweede gecontroleerde referentie of expliciete herijking vereisen. Het herhaald zien van één tag bewijst niet dat die tag vastzit. Deze reparatie herstelt geen aantoonbaar foutieve beginoriëntatie met één tag.

De diagnostiek bewaart ruwe mm/graden, samples, gebruikte/uitgesloten tags en meetleeftijd. De suffix `rotation-held` maakt de beperkte correctie herkenbaar. De maximale hoekpuntafwijking wordt in px getoond. De bestaande begrensde logcadans blijft intact. Een herstelmelding zegt nu expliciet dat een tag gezien is en de uitlijning wordt afgewezen.

## Bediening

- Cursor: naam/ID en `Vastleggen`, `Opnieuw vastleggen` of `Doelgebied voorbereiden`. Geen misleidend `geen pose` als alleen het gekozen vlak niet geraakt wordt.
- Camera-ingang kiest de eerste nog te plaatsen sensor. Na fysiek vastleggen volgt de volgende Pending in projectvolgorde, met terugloop naar eerder overgeslagen sensoren. Is alles geplaatst, dan wordt een nieuwe ID klaargezet.
- Vorige/volgende doorlopen alle bestaande sensoren en daarna de nieuwe sensor. Kiezen verandert geen opgeslagen meting. Een eerder geplaatste sensor terugkiezen toont `Opnieuw vastleggen`.
- De teller bovenin opent een compacte lijst met zoeken op naam/ID en status per sensor. Sensor-ID en optionele fysieke tag-ID blijven afzonderlijk.
- Onderaan staat één opnameknop, tussen vorige/volgende. De dubbele tekstknop en Sensor/Tag-keuze zijn verwijderd. Taginstellingen en tagopslag blijven in de Tags-FAB en 2D-voorbereiding.
- Een sensor op een ander vlak kiezen wist de oude cursor/straal tot het volgende renderframe. Snel opnieuw drukken kan daardoor geen positie op het vorige vlak vastleggen.
- AR-menu: vaste hoogte op basis van schermruimte, een vaste sluitknop van 48 dp en afzonderlijk scrollende inhoud. Meldingen worden niet achter een open menu getoond.

## Verificatie

`singleReferenceTranslationDriftWithWeakTiltDoesNotLockOutRecovery` is eerst uitgevoerd vóór de reparatie: 1 test, 1 failure (`app/build/single-tag-recovery-before.txt`). Het scenario biedt een consistente verschuiving van 20 mm, een ruwe normaalafwijking van 5° en vier geometrisch overeenkomende hoeken. De oude route bleef geblokkeerd; de aangepaste route moet de positie herstellen en de oriëntatie behouden.

Aanvullende regressies controleren grotere verplaatsingen/waarneembare rotaties, ongeldige of te kleine hoekbeelden, overeenkomende en tegenstrijdige tagsets, uitsluiting, stale/herhaalde pakketten, bewegende opname-/weergavecamera en afronden zonder nieuwe beelden. Native OpenCV wordt op de emulator met deterministische hoekpuntruis en de echte fusionketen getest. Bedieningsproeven gebruiken de echte Compose-componenten met een stilstaand testvlak; opslagproeven gebruiken echte WorkflowAppState en geïsoleerde JSON-opslag.

Definitief: 195 JVM-tests, 0 failures/errors/skipped; `testDebugUnitTest`, `assembleDebug` en `assembleDebugAndroidTest` geslaagd (`app/build/camera-update-final-build.txt`). 20 emulatortests geslaagd op API 37 in aparte runs: 3 bediening (`camera-controls-final-tests.txt`), 7 opslag/werkwijze + 5 native pose (`camera-state-pose-final-tests.txt`), 5 kaartbediening (`camera-map-final-tests.txt`). Schermafbeeldingen van cursor, sensorlijst en menu zijn visueel gecontroleerd.

De eerste gecombineerde emulatorrun stopte tijdens de laatste kaarttest door LOW_MEMORY (vastgesteld met Android ApplicationExitInfo); daarom opnieuw in kleinere groepen uitgevoerd. Een eerdere UI-test legde ook de onduidelijke oude statuslabels bloot; de lijst toont nu Te plaatsen / Geplaatst / Geplaatst · afwijking. Definitieve runs slagen.

## Nog op de telefoon meten

1. Dezelfde scène op circa 0,36, 0,6 en 0,9 m: blauwe/paarse contour, hoekpuntafwijking in px, ruwe mm/graden en herstelstatus opnemen. Controleer fysieke zwarte tagmaat, vlakheid, opgegeven trafovlak en rotatie.
2. Eén gecontroleerde tag: stilhouden, zijwaarts bewegen en terugkomen. Een kleine positiecorrectie mag automatisch herstellen; een werkelijk grote of hoekige afwijking moet de herstelroute aangeven.
3. Twee ruim uit elkaar liggende referenties samen zien; vervolgens een derde bewust verschuiven. Vergelijk gebruikte en uitgesloten IDs en meet de overlay bij sensoren verder van de referenties.
4. Sensor vastleggen, automatische volgende selectie, oudere sensor terugkiezen en elders opnieuw vastleggen; controleer dat doelradius, ID en overige metingen bewaard blijven.
5. AR-menu met wisselende diagnostiek sluiten en opnieuw openen, ook met grotere systeemletters en liggend scherm.

Er is geen geslaagde fysieke toesteltest of millimeternauwkeurigheid geclaimd. Geen push uitgevoerd.
