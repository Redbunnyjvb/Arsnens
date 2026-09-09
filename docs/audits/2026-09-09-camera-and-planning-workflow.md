# Camera en voorbereiden: doel, audit en implementatie

## Bedoelde werkwijze

Eén project beschrijft de trafo, referentietags en sensoren. **Camera** is de ingang voor fysiek vastleggen. **2D** is de werkruimte voor bekijken, meten en voorbereiden. De ChatGPT-bijlage is gebruikt als ontwerpvoorstel, niet als zelfstandige opdracht of als bewijs dat de bestaande code fout is.

Een sensor heeft een vaste **sensor-ID**, een bewerkbare **naam** en eventueel een **sensor-tag-ID**. Bijvoorbeeld `TEMP-A · Temperatuur tank` met AprilTag `200`. Deze drie waarden hebben verschillende functies. Dezelfde naam mag vaker voorkomen; een sensor-tag mag binnen een project maar aan één sensor gekoppeld zijn.

Een voorbereide sensor heeft een doelpositie en doelradius. Standaard is dat **50 mm = 5 cm**, niet de 0,50 m uit de afbeelding. De bestaande AR-doelcirkel blijft deze radius gebruiken. Het is een plaatsingsgebied, geen belofte over AR-meetnauwkeurigheid.

Bij **Sensor zit hier** wordt de werkelijke positie apart vastgelegd. Buiten de radius plaatsen is toegestaan: doel en meting blijven beide bewaard, met status **Afwijking**. Een operator kan dus bewust ergens anders monteren zonder het plan stilzwijgend te verplaatsen. De expliciete 2D-actie **Verplaatsen** verandert juist het doel. Bij een bestaande meting wordt de afwijking opnieuw berekend; de meting, foto en opnametijd blijven behouden.

## Bewezen problemen en reparaties

| Bevinding | Reparatie |
|---|---|
| 2D begon afhankelijk van de ingang direct in plaatsen of meten. In voorbereiden kon een tik op een bestaand object een plaatsingsactie uitvoeren. | Eén bestaande werkruimte met standaard Selecteren en expliciete modi Meten/Voorbereiden. Een bestaand object aantikken selecteert het. |
| Kaartselectie leidde het type af uit het getoonde label. Sensor `T0` werd bij aanwezigheid van tag 0 als tag geselecteerd. | Selectie bewaart het objecttype en de ID afzonderlijk. De regressietest faalde eerst op de oude selectiecode en slaagt na herstel. |
| Tijdens slepen werd direct opgeslagen; Annuleren kon dat niet terugdraaien. | Slepen maakt een tijdelijke positie. Alleen Opslaan roept de wijzigingsactie aan. Alleen het geselecteerde object kan worden versleept. |
| Cameraplaatsing kon de gekozen sensor-ID vervangen door een uit de tag afgeleid nummer. | De gekozen ID blijft leidend. Een al gekoppelde andere sensor-tag geeft een concrete melding; geen automatische hernummering. |
| “Hier komt een sensor” gaf een nieuwe sensor een fysieke audit en camera-herkomst. | Voorbereiden geeft Pending/Prepared zonder plaatsingsaudit of meetresultaat, ook vanuit de camera. |
| De oude installatieroute bewaarde de meetstatus zonder een nieuwe plaatsingsaudit. | Een bevestigde meting bewaart ook de kwaliteitsregistratie. Opnieuw bevestigen vereist actuele plaatsingskwaliteit; een vlakwissel wist een klaargezette scan. |
| Verwijderen liet oude meetresultaten achter; undo verwijderde simpelweg de sensor met de hoogste volgorde. | Verwijderen ruimt het bijbehorende actuele resultaat op. Undo zet de laatste bevestigde plaatsing terug op te plaatsen en bewaart de sensor. |
| Tellers noemden ook voorbereide sensoren “geplaatst”. | Tellers gebruiken daadwerkelijk bevestigde statussen: geplaatst / totaal. |

## Bediening

- 2D: Selecteren, Meten, Voorbereiden; daarnaast Lagen, Lijst en Fit.
- De lijst zoekt op naam of ID en bevat afzonderlijke tabbladen voor sensoren en tags. Selectie via de lijst of Vorige/Volgende kiest het bijbehorende buitenaanzicht zonder plaatsing te bevestigen.
- De kaart toont het gekozen trafovlak, de doelradius op schaal en waar beschikbaar de afzonderlijke gemeten positie.
- Een sensor bewerken kan naam, radius, sensor-tag en instructie aanpassen. De ID blijft vast. Een leeg tagveld ontkoppelt de tag.
- Via **Meer → Met camera plaatsen** neem je de gekozen sensor mee naar de camera. In de camera zijn **Sensor kiezen** en **Nieuwe sensor** expliciete acties.
- Na bevestigen blijft de gekozen sensor geselecteerd. Een tweede tik maakt daardoor niet ongemerkt een nieuw nummer aan.
- **Opnieuw te plaatsen** bewaart ID, naam, doel, radius en tagkoppeling; het actuele resultaat, de fysieke audit en tijdelijke correcties worden gewist. De huidige log bewaart één actuele bevestiging per sensor; dit is geen nieuw historisch logboek.

## Compatibiliteit en grenzen

De Android JSON-velden en wirewaarden blijven gelijk: `position_mm`, `tolerance_mm`, `sensor_tag_id`, `placement`, `origin`, `pending/ok/fail`, `prepared/on_the_fly`. Alleen zichtbare terminologie verandert. De [JSON-reader van Arsensprogram](https://github.com/Redbunnyjvb/Arsensprogram/blob/main/arsens_project_editor/app/json_io.py) en het [sensormodel](https://github.com/Redbunnyjvb/Arsensprogram/blob/main/arsens_project_editor/app/models.py) zijn alleen-lezen gecontroleerd. De pc-app is niet gewijzigd.

Het native AR-anker, opnameframe-koppeling en posefilter zijn in deze wijziging niet vervangen. De bestaande AR-doelcirkel blijft gebruikt. De afbeelding is als functionele indeling verwerkt, niet als exacte grafische kopie of nieuwe 3D-cone-renderer. De standaard uitgeschakelde optionele straal-replay van reeds vastgelegde camera-sensoren is geen onderdeel van deze UI-herziening; voorbereide doelen krijgen geen nieuwe replay-straal.

## Bestanden

- `ExtendedScreens.kt`: bestaande 2D-werkruimte, gescheiden selectie-ID's, tijdelijke verplaatsing, lijst en radius.
- `WorkflowAppState.kt`: naam/ID/tagvalidatie, voorbereiden versus meten, reset en bewaarroutes.
- `SensorPlanning.kt`: gedeelde logica voor doelwijziging, reset, tagconflicten en tellers.
- `WorkflowSensorEditor.kt`, `WorkflowSensorScreens.kt`, `WorkflowReportScreens.kt`, `WorkflowTagScreens.kt`: bewerken, camerakeuze en aansluiting van de 2D-werkruimte.
- `ArSensHomeScreens.kt`, `Models.kt`, `WorkflowStlScreen.kt`: Camera/2D, statuslabels en correcte tellers.
- `gradle/libs.versions.toml`: uitsluitend AndroidX-testafhankelijkheden bijgewerkt. Espresso 3.5.1 crashte vóór UI-bediening op Android 37 door `InputManager.getInstance`; [Espresso 3.7.0 bevat de gerichte reparatie](https://developer.android.com/jetpack/androidx/releases/test#espresso-3.7.0).

## Verificatie

- `MapSelectionIdentityTest.sensorNamedLikeATagRemainsASensor`: eerst AssertionError op de oude selectiecode, daarna geslaagd.
- 189 JVM-tests geslaagd, 0 failures/errors/skipped. Inclusief radiusgrens, buiten radius plaatsen, doel wijzigen met behoud van meting/tijd, reset, herhaalde bevestiging, tagconflicten, teller en een pc-projectfixture met JSON-rondrit.
- `testDebugUnitTest`, `assembleDebug` en `assembleDebugAndroidTest`: geslaagd.
- 9 emulatortests geslaagd op Android 37: bediening van selecteren/voorbereiden/zoeken/meten en opslaan/annuleren van een verplaatsing; daarnaast echte WorkflowAppState- en JSON-opslag met geïsoleerde testgegevens voor 2D → camera → afwijking → reset → heropenen, camera-voorbereiding, vrije camerameting en tagontkoppeling. Definitieve uitvoer: `app/build/workflow-emulator-tests.txt`.
- Testprojecten gebruiken eigen cacheopslag. Er zijn geen gebruikersprojecten gewist, geen commits gemaakt en geen wijzigingen gepusht.

## Nog op de telefoon controleren

1. Eigen pc-project openen: naam/ID, radius en buitenaanzicht vergelijken met de fysieke trafo.
2. Geselecteerde sensor binnen én buiten 50 mm bevestigen, eventueel op een ander gekozen trafovlak; doel en meting in AR en rapport vergelijken.
3. Sensor-tag 200 koppelen en dezelfde tag bij een andere geselecteerde sensor proberen; de app moet de keuze expliciet laten corrigeren.
4. Onder werkelijke camerabeweging, verlichting, tagafstand en AR-drift controleren of de overlays en plaatsing bruikbaar blijven. Emulatortests met gecontroleerde AR-pakketten bewijzen geen fysieke nauwkeurigheid of trillingsvrije tracking.

Er was tijdens deze uitvoering geen fysieke telefoon aangesloten. De APK staat in `app/build/outputs/apk/debug/app-debug.apk`.
