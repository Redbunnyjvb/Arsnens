# Poseverbetering en controle van de on-the-fly-flow

Aanvulling: de opname van 21:40 leidde tot een [vervolgaudit van de wegvallende plaatsingsmogelijkheid](2026-09-08-visible-tag-lock-audit.md). Die audit beschrijft en corrigeert een regressie in de hier genoemde stabilisatie; de hieronder beschreven eerste implementatie is daarvoor niet het eindresultaat.

Dit vervolgt de audit van 8 september 2026. De verbeteringen zijn in de gedeelde werkmap uitgevoerd. Tijdens het werk is de basischeckout bijgewerkt van `f4f63cc` naar `1de673a`; de tussentijds opgeslagen wijzigingen zijn behouden.

## Bedoelde werking

De transformator heeft één vast coördinatenstelsel. Referentietags liggen op bekende posities en bepalen waar dat stelsel in de ARCore-wereld staat. Sensor-tags identificeren sensoren en worden door detectie alleen geen kalibratiereferentie. Model, cursor en sensoroverlays delen het wereldanker. ARCore volgt de telefoon tussen tagmetingen; nieuwe betrouwbare tagmetingen corrigeren het anker.

Een sensor kan gepland of gemeten zijn. De voorbereide positie blijft de doelpositie; een bevestigingsmeting krijgt een eigen plaats in het installatielog en wordt getoetst aan de tolerantie. De live flow biedt nu ook “Hier komt een sensor”. Geplande plekken krijgen een cirkel in het werkelijke vlak met standaard 50 mm straal, onafhankelijk van afstand en schermresolutie. Die straal is de gewenste plaatsingszone, geen claim over meetnauwkeurigheid.

Het gekozen vlak wordt van buiten bekeken:

- Voor: rechts = +X, omhoog = +Z.
- Achter: rechts = -X, omhoog = +Z.
- Links: rechts = -Y, omhoog = +Z.
- Rechts: rechts = +Y, omhoog = +Z.
- Boven: rechts = +X, omhoog op de kaart = +Y (achter); voorzijde onderaan.

De bestaande bediening voldoet aan deze assenconventie en is behouden. Een volledige nieuwe uitklapbare kaart of uitbreiding van het externe Python-voorbereidingsprogramma is niet gebouwd. Rasterpunten stellen tagcentra voor: het opgeslagen punt is het midden van het zwarte vierkant. Voor meten tot een hoek zijn de bestaande meetankerinstellingen relevant. “Midden onder” exact op Z=0 betekent een tagcentrum op Z=0, niet een tag waarvan de onderrand op Z=0 ligt.

## Wat de ontvangen log bewijst

De aangeleverde opname van 14:15:09–14:15:11 bevat:

- `transformerPose=true`, `displayProjection=true`;
- `poseTags=[]`, `posePerTag=[]`;
- `stableLock=false`, `grade=Low`, `q=45`, “drift mogelijk”.

Er is dus een bestaand ARCore-anker in gebruik, zonder een verse tagpose die op dat moment kalibreert. De getoonde oude fit van ongeveer 0,63 px bewijst niet dat de zichtbare tag nu meedoet. In de tussenversie kon de leeftijd bovendien vanaf een geanalyseerd beeld zonder tags lopen; zo'n lege detectie wordt nu niet meer als verse tagwaarneming weergegeven.

De log bevat geen herkende tag-ID, opgeslagen markergeometrie of beslisregel van de detector op dat moment. Er kan daarom uit deze opname niet worden vastgesteld of de tag ongelezen, onbekend, uitgeschakeld, afgewezen op fit of uitgesloten was. De ARCore-waarschuwingen over visuele kenmerken zijn evenmin bewijs van een specifieke fout in de taghoekvolgorde. Een uitleespoging van de toestelinstellingen kon niet worden afgerond: het toestel was bij de controle niet meer verbonden.

Er zijn nu INFO-regels onder `ARsensPose` en `ARSensFusion` voor:

1. herkende IDs en actieve opgeslagen IDs met maat en XYZ;
2. gebruikte en uitgesloten referentietags;
3. acceptatie, wachten of afwijzen, met reden;
4. echte opnameleeftijd, tracking en rust van het anker.

De tag- en AR-menu's tonen ook een concrete diagnose. Het relevante logfilter is bijvoorbeeld `adb logcat -s ARsensPose ARSensFusion`. Een nieuwe log met deze twee tags kan de resterende toestelvraag beantwoorden.

## Uitgevoerde verbeteringen

### Posekeuze

`TagPoseConsensus.kt` eist bij meerdere zichtbare referenties een strikte meerderheid. Een enkele leesbare tag kan wel een eerste pose opleveren. De hypothesen komen van individuele tags, tagparen en — onder een strengere foutgrens — de volledige set. Ieder lid van een groep wordt gecontroleerd op de slechtst passende hoek. Een aantoonbaar schonere meerderheid kan een gezamenlijke compromispose verdringen; bijna gelijke alternatieve groepen worden als ambigu behandeld.

De oorspronkelijke eerste return op een goed passende individuele tag is verwijderd. Ook de bestaande modus “Dichtstbijzijnde tag” moet door de onderlinge controle en kan een gezamenlijke oplossing gebruiken wanneer zijn lokale pose onvoldoende op de groep past. De nieuwe standaard heet “Automatisch”.

Gezamenlijke poses worden na de initiële PnP-oplossing verfijnd met OpenCV `solvePnPRefineLM`, voordat hun hoekfouten worden beoordeeld. Dit verbetert de acceptatie van correct geplaatste tags bij beeldruis; de afzonderlijke tagcontroles blijven daarna gelden.

Een uitgesloten referentie kan niet onmiddellijk als enige zichtbare tag het anker overnemen. Herstel vereist drie opeenvolgende groepswaarnemingen. Markerwijzigingen en strategiewijzigingen wissen de betreffende detectorhistorie. Niet-eindige poses, achter de camera liggende oplossingen en dubbele zichtbare IDs worden geweigerd. Native object- en beeldmatrices van de gezamenlijke solver worden expliciet vrijgegeven.

### Anker en vernieuwing

`AnchorPoseFilter.kt` filtert de objecttransformatie in wereldcoördinaten; de camerabeweging wordt niet afgeremd. Kleine ankerafwijkingen binnen 3 mm en 0,15° worden vastgehouden. Andere correcties vereisen ten minste drie onafhankelijke waarnemingen en 200 ms rust. Daarna wordt het bestaande anker geleidelijk bijgesteld.

Afstanden voor stabilisatie worden bij de gebruikte referentie gemeten, niet alleen bij de projectoorsprong. Een tag in het midden van een grote tank wordt daardoor niet onterecht als grote translatie behandeld doordat kleine rotatieruis ver van de oorsprong wordt uitvergroot.

Een grote afwijking van één tag verplaatst een bestaand anker niet automatisch. Meerdere overeenstemmende tags kunnen een grotere correctie leveren na minimaal acht waarnemingen en een seconde rust. Trackingverlies heeft een afzonderlijk herlokalisatiepad. “AR opnieuw ijken” wist bewust het anker en laat het opnieuw opbouwen, zonder opgeslagen tag- of sensorposities te wijzigen.

Opnamepose, monotone opnametijd en detectiesequentie reizen mee met het beeld. Dezelfde cameraframe wordt niet herhaald als nieuwe detectie. Eén detectie telt niet meerdere keren mee doordat de UI vaker tekent. Een analyse ouder dan 250 ms mag het anker niet bijwerken. Oude resultaten van vóór een kalibratiereset worden door een revisiecontrole genegeerd.

### Eén frame en één plaatsingsvlak

De cursor gebruikt dezelfde actuele ARCore-projectie als model en sensoren. De sensor-tagmeting gebruikt de oorspronkelijke beeldpixel met de bijbehorende opnamecamera, omgerekend via hetzelfde wereldanker. De straal voor optionele replay is de werkelijk gebruikte plaatsingsstraal; ook het gekozen trafovlak wordt opgeslagen.

Cursor, sensor-tagcentrum en replay snijden nu consequent het gekozen nominale trafovlak. Dit is bewust geen claim van een meting op elke rib of elk willekeurig STL-driehoekje. Voor zulke oppervlakken is een expliciete oppervlakdefinitie of een geschikte mesh-raycast nodig. Die uitbreiding moet apart worden uitgewerkt en getest.

De automatische STL-snap na tagopslag is verwijderd. Een handmatig gemeten tagdiepte of buitenwaartse offset wordt niet meer ongevraagd vervangen door een modeloppervlak. De expliciete actie om een tag op het modeloppervlak te zetten blijft beschikbaar.

Een oude schermprojectie wordt bij trackingverlies niet meer drie seconden vastgehouden: dat kon het model aan het scherm laten kleven. Als alleen tags uit beeld verdwijnen terwijl ARCore trackt, blijft de actuele projectie gewoon bestaan.

### Sensorstatus en kwaliteit

De live sensorflow onderscheidt plannen en vastleggen. Een bestaande geplande doelpositie en zijn tolerantie blijven behouden bij een meting. De sensorstatus volgt de daadwerkelijke tolerantiecontrole, zodat een afgekeurde meting niet alsnog als OK in de sensorlijst komt. Vaste sensoroverlays gebruiken de gelogde meetpositie waar die beschikbaar is; het doel blijft apart beschikbaar.

Een onrustig of tegenstrijdig anker mag geen nieuwe sensorplaatsing vastleggen. De kwaliteitsweergave benoemt uitsluiting en conflicten. Groen vereist tevens een stabiele lock. De px→mm-indicatie gebruikt nu de cameradiepte van de gebruikte referentie, niet de afstand tot de projectoorsprong.

## Verificatie en grenzen

`testDebugUnitTest`, `assembleDebug` en `assembleDebugAndroidTest` zijn geslaagd. De 136 JVM-tests hebben nul fouten, nul mislukkingen en nul overgeslagen tests.

De JVM-regressies controleren onder meer een verschoven derde tag, tegenstrijdige paren, bijna gelijke alternatieven, een laagfoutige compromispose, uitsluiting en herstel, enkel-taginitialisatie, kleine jitter, grotere correcties, alle buitenaanzichten en een fysieke straal van 50 mm. De bestaande project-, geometrie-, import- en opslagtaken blijven meegenomen in de unit-tests.

Er is een Android-instrumentatietest toegevoegd voor de werkelijke Kotlin/OpenCV-keten: een voorste tag midden onder, drie tags waarvan één 50 mm is verschoven, en oude/herhaalde capture-pakketten. Het testpakket is gebouwd; deze instrumentatietests zijn niet op de telefoon uitgevoerd. Er zijn geen projectgegevens op de telefoon gewijzigd en er is geen APK door deze taak geïnstalleerd.

Daarnaast is `scripts/verify_pose_geometry.py` uitgevoerd met OpenCV 4.13.0 in een tijdelijke Python-testomgeving. Dit is een onafhankelijke numerieke controle, niet een uitvoering van de Kotlin-app. Bevestigd:

- Een voorste tag op X=820 en X=4000 mm, Z=50 mm, levert in het ideale gesimuleerde beeld de correcte camera terug binnen 2 mm.
- De vijf buitenvlakconventies leveren in ideale beelden de correcte camerapose.
- De hoekverschuiving van twee posities correspondeert met de officiële voorbeeldprints ID 0 van tag36h11, tag25h9 en tag16h5.
- In de gekozen drie-taggeometrie wordt een verschuiving van 20 of 50 mm zonder hoekruis in alle 100 herhalingen uitgesloten.

De ruisproef is ook relevant. Na de gezamenlijke verfijning worden bij drie correct geplaatste tags alle drie geaccepteerd in 100/100 beelden met 0,25 px gesimuleerde hoekruis, en in 98/100 beelden met 0,5 px ruis (twee ambigu). Bij 0,25 px ruis en 50 mm verschuiving koos de controle in 100/100 beelden de twee correcte tags. Bij 10 mm verschuiving en dezelfde ruis paste in 67/100 beelden toch een gezamenlijke groep binnen de criteria; 32 beelden waren ambigu en één koos het correcte paar. Bij 20 mm verschuiving waren 75/100 beelden ambigu en koos de rest het correcte paar. In deze definitieve proef werd geen verkeerd paar gekozen, maar dat bewijst geen foutloze fysieke uitlijning. De gekozen geometrie en ruisverdeling zijn geen universele nauwkeurigheidsmeting.

**Geen foutloosheidsclaim:** een fysiek verkeerd geplaatste enkele referentie is zonder andere betrouwbare informatie niet te onderscheiden van een gewijzigd objectanker. Kleine afwijkingen kunnen binnen de beeldruis passen. Verkeerde schaal, printmaat of een gemeenschappelijke fout in alle referenties wordt niet opgelost door consensus. De juiste werking op het specifieke toestel en met de fysieke tags moet nog worden beproefd.

Voor een praktijktest: begin met één volledig zichtbare bekende voorste tag en controleer maat, centrum-XYZ en printoriëntatie; loop daarna met twee/drie correct gemeten tags om de trafo; verplaats één tag gecontroleerd met 10/20/50 mm; meet geplande en werkelijke sensorposities op meerdere vlakken. Controleer de logredenen en onafhankelijk gemeten XYZ, niet alleen de rust van het beeld.

Primaire achtergrond: [OpenCV PnP](https://docs.opencv.org/doc/doxygen/html/d5/d1f/calib3d_solvePnP.html) en [officiële AprilRobotics-prints](https://github.com/AprilRobotics/apriltag-imgs).
