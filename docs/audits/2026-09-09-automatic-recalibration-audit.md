# Afstandsinstelling en gerichte audit automatische herijking

De door de gebruiker aangeleverde hypotheses zijn eerst in de huidige code gecontroleerd. De afstandsinstelling is als eerste geïmplementeerd en gebouwd. Daarna zijn nieuwe regressies uitgevoerd vóór aanpassing van het ankerfilter en de fusiebeslissingen.

## Afstand naast de tag

**Instellingen → Tags → Afstand tot tag weergeven** is een blijvende appinstelling, standaard uit. Aan toont bijvoorbeeld `≈ 0,62 m` naast een vers herkende referentietag, ook als de gewone tagcontourlaag uitstaat. De berekening gebruikt de gemeten pose, het ingestelde fysieke tagformaat en de huidige fysieke camera. Het gaat om de rechte afstand tot het tagcentrum, niet alleen de optische Z-diepte of de afstand tot de projectoorsprong. De afstand is niet afgeleid van de gefuseerde modelpose.

Er wordt geen afstand verzonnen voor onbekende tags of sensor-tags zonder referentiepose. Na het versheidsvenster van 250 ms verdwijnt de afstand bij de waarneming. De weergegeven schatting bewijst geen meetnauwkeurigheid; een onjuiste fysieke tagmaat beïnvloedt ook de afstand.

## Bewezen fouten en herstel

| Bevinding | Bewijs vóór herstel | Reparatie |
|---|---|---|
| Wisselende tagsets blokkeerden gewone herijking | Bij 20 mm constante correctie en wisselend `[0]` / `[0,1]` bleef de pose onbruikbaar voor plaatsing. Het pending venster begon telkens opnieuw. | De wachtende hypothese wordt ook bij gewone herijking tegen alle nieuw bevestigende referentiehoeken gecontroleerd. Overeenkomende sets tellen samen mee; een geometrische tegenspraak reset het venster. |
| Een bevestigde correctie werd afgebroken | De filtertest bleef na beeldbevestiging op 3,6 mm van een doel van 24 mm staan. De fusietest stopte op 21,239 mm. | `anchor-held` en beeldbevestiging annuleren een lopend, bevestigd doel niet meer. Het tijdgestuurde bijstellen loopt door. Nieuwe conflicten en onbruikbare grote sprongen behouden hun afbreekgedrag. |
| Een kleine pixelfout maskeerde dieptedrift | Een consistent 20 mm verschoven tagvlak op circa 1,2 m afstand werd met minder dan 1,5 px hoekfout voortdurend vastgehouden; de correctie bleef 0 mm. | Pixel-hold vereist nu ook dat het gemeten referentiecentrum binnen 8 mm van het bestaande anker ligt. Die 8 mm sluit aan op de bestaande bruikbaarheidsgrens voor een bijstelling. Het is een lokale ruisband, geen grens voor de totale fout rondom de trafo. |
| Een verouderd conflict trok een huidige kalibratie in | Een analysepakket van 600 ms oud activeerde `referenceConflictActive` en blokkeerde plaatsing. | Leeftijd en cameratracking worden gecontroleerd voordat een conflict blijvend wordt gemaakt. |
| Fusie vertrouwde een gebruikte, uitgesloten ID | Een synthetisch pakket met dezelfde ID in gebruikt én uitgesloten kon verder verwerkt worden. | Een expliciete controle wijst dit tegenstrijdige pakket af. Dit is extra bescherming van de aanroepgrens; de normale detector hoort zo'n pakket niet te produceren. |
| Een PAUSED native anker stopte ook de detector | De renderer startte detectie uitsluitend zonder anker of met een TRACKING anker. Camera TRACKING + anker PAUSED kon dus geen nieuw tagbewijs opbouwen. | Detectie gaat in dat geval door in een afzonderlijk tijdelijk wereldframe. De gepauzeerde ankerpose wordt nergens gelezen voor deze herstelroute. |

De eerste gerichte uitvoering had **32 tests, waarvan 8 faalden**. Naast bovenstaande gevallen faalden de filtertest met wisselende bevestigde sets en de hersteltest van grote naar kleine drift op 17,699 mm in plaats van 20 mm. Die laatste twee afwijkingen werden eveneens veroorzaakt door het voortijdig stoppen van een bevestigde correctie. Dezelfde tests slagen na herstel.

### Spronggrenzen en een bruikbare herstelroute

De grenzen voor één referentie zijn niet verruimd: boven 30 mm of 1,5° ten opzichte van een bestaand anker blijft een enkele tag onvoldoende voor automatische overname. Herhaaldelijk dezelfde tag zien kan niet bewijzen dat die tag niet verplaatst is. Bij aanhoudende afwijking vraagt de diagnostiek om twee gecontroleerde referenties tegelijk, of controle van de fysieke tagpositie gevolgd door **AR opnieuw ijken**.

Kleine consistente correcties kunnen ook na `requiresRecalibration` opnieuw bewijs opbouwen en herstellen. Voor grotere correcties kunnen meerdere daadwerkelijk overeenkomende referenties gebruikt worden; grote sprongen blijven een langer meetvenster vereisen. De regressies behandelen 20, 70 en 200 mm, plus tegenstrijdige en uitgesloten referenties.

Bij **Dichtstbijzijnde tag** kan de gekozen pose uit één tag komen, terwijl de detector de pose tegen meerdere tags controleert. Die informatie ging voorheen verloren voor de vervolgketen. `verifiedReferenceIds` geeft nu de volledig gecontroleerde, niet-gequarantaineerde referenties door aan beeldvalidatie, het filter en native herstel. Het aantal zichtbare tags wordt niet zomaar als bewijs gebruikt.

### Gecontroleerd native herstel

Een gepauzeerd anker krijgt eerst gelegenheid vanzelf terug te keren. Na minimaal één seconde PAUSED kan automatisch een vervangend native anker worden gemaakt, mits minimaal vijf onafhankelijke, geometrisch overeenkomende metingen over 400 ms het ondersteunen en elke gebruikte meting ten minste twee gecontroleerde referenties bevat. Het nieuwe frame doorloopt daarna de gewone kalibratie voordat plaatsing beschikbaar is.

De tijdelijke opnamen krijgen een eigen frame-ID. Native hervatten, camera-trackingverlies, STOPPED of een ander actief anker wissen dit herstelbewijs. Herhaalde, verouderde, conflicterende en uitgesloten pakketten tellen niet mee. Korte onderbrekingen kunnen worden overbrugd; na meer dan 500 ms zonder goed bewijs is een nieuw meetvenster nodig.

De oude native ankerpose wordt niet gebruikt tijdens PAUSED. Een nieuw anker wordt alleen gelezen als het TRACKING is. Dit volgt de [ARCore Anchor-documentatie](https://developers.google.com/ar/reference/java/com/google/ar/core/Anchor), die een pose buiten TRACKING niet bruikbaar noemt. Een terugkerend bestaand anker behoudt zijn frame; STOPPED leidt tot vervanging. De fusie controleert zelfstandig frame-ID's, naast de renderercontrole. Oude UI-resultaten kunnen een nieuw frame niet terugdraaien.

## Automatisch versus ‘AR opnieuw ijken’

De volledige knoproute is `recalibrateAr()` → `resetArPoseState()` → hogere `arCalibrationRevision` → gewijzigde markersignaturen in de renderer → `resetTrackingReference()`.

De UI wist daarbij de tijdelijke sensortagmeting, cursorstraal, resultaat, cursorstatus, referentiekeuze, kwaliteits-/lockbuffers en sessiegebonden plaatsingsstralen. De renderer detacht het native anker, wist fusie en herstelbewijs, geeft het nieuwe frame een nieuw ID en verwerpt oude analysepakketten. Daarna begint de eerste kalibratie opnieuw, zonder vergelijking met het oude anker. Daarom kan de knop een afwijking overnemen die een automatische enkel-tagcorrectie terecht niet zelfstandig vertrouwt.

Deze knop herschrijft geen opgeslagen sensorcoördinaten en wist geen project. Ook wist hij bij ongewijzigde taggeometrie niet stilzwijgend de detectorquarantaine. Uitgesloten referenties moeten nog steeds via de bestaande meervoudige bevestiging herstellen.

Automatische gewone correcties houden hetzelfde gedeelde native frame voor model, sensoren en cursor. Alleen noodzakelijk native herstel vervangt dat frame. Bij framewisseling worden oude sessiestralen en tijdelijke meetgegevens ongeldig gemaakt. De bestaande opt-in voor sensorstraal-replay blijft ongewijzigd; deze audit heeft geen opgeslagen sensorposities aangepast.

## Diagnostiek

De AR-opties tonen nu afzonderlijk: eerste kalibratie, anker bevestigd, correctie opbouwen, correctie uitvoeren, extra referenties/bevestiging nodig en native herstel. Daar staan reden, gekozen en bevestigende tags, uitgesloten tags, meetleeftijd, afwijking in mm/graden en sampleaantal bij. De fusielogs zijn begrensd tot maximaal twee beslissingen per seconde; ongewijzigde beslissingen maximaal eenmaal per seconde. Detectorlogging is eveneens per categorie begrensd, ook bij wisselende tagsets.

## Gewijzigde bestanden

- `AppSettings.kt`, `WorkflowAppState.kt`, `ArSensHomeScreens.kt`: opgeslagen afstandsschakelaar, framewisseling en tijdelijke UI-meetgegevens.
- `TagObservationProjection.kt`, `WorkflowArOverlays.kt`: afstandsberekening en afstand naast de tag.
- `AprilTagDetector.kt`: bevestigende referenties, resultaatdiagnostiek en logbegrenzing.
- `AnchorPoseFilter.kt`: consistente meetvensters, expliciete correctietoestand en afronden van bevestigde doelen.
- `ArCoreCameraPanel.kt`: beeldcontrole, native herstelkoppeling, framegrenzen en aansturing van overlays/plaatsing.
- `NativeAnchorRecovery.kt`: geïsoleerde, geteste herstelroute voor PAUSED.
- `WorkflowCameraChrome.kt`: bestaande AR-diagnostiek uitgebreid.
- `AnchorPoseFilterTest.kt`, `ArCoreFusionRegressionTest.kt`, `TagDistanceTest.kt`, `PosePipelineInstrumentedTest.kt`: regressies en native controles.

## Verificatie en resterende telefooncontrole

**178 JVM-tests geslaagd**, nul mislukkingen, fouten of overgeslagen tests. `testDebugUnitTest`, `assembleDebug` en `assembleDebugAndroidTest` zijn geslaagd met de lokale offline Gradle-cache. `git diff --check` meldt geen whitespacefouten.

**4 native tests geslaagd op de Samsung SM-S938B** met `PosePipelineInstrumentedTest`: pose van een tag midden onder aan de voorkant, uitsluiting van één verschoven referentie tussen drie tags, de onafhankelijk gecontroleerde tweede referentie in NearestTag-modus, en afwijzing van verouderde/herhaalde pakketten. AndroidJUnitRunner rapporteerde `OK (4 tests)`. Dit zijn geometrie- en codeproeven op het toestel, geen geslaagde proef met de echte AR-camera.

De APK is met `adb install -r` als update geïnstalleerd op de aangesloten **Samsung SM-S938B**, zonder appdata te wissen. De native tests openen geen Activity, camera of projectopslag. Er is niet gepusht.

Nog fysiek controleren: afstand bij correct opgemeten prints; automatische herijking na echte drift terwijl de zichtbare tagset wisselt; plaatsing zonder voortdurend zichtbare tags; een verschoven derde tag; werkelijk PAUSED/TRACKING/STOPPED met kaartverfijning. De modeltests van deze toestanden bewijzen de codebeslissingen, niet het gedrag van de ARCore-sessie op locatie. Cameratrilling, rolling shutter en absolute plaatsingsnauwkeurigheid zijn niet door de native geometrieproeven gemeten.
