# Eén tag per wand — 11 september 2026

## Verzoek en oorzaak

De gebruiker bedoelt één tag **per wand**, ook voor een kleine kast. De wandsolver eiste minstens twee tags per zijwand en daarna een horizontale spreiding van minstens `max(100 mm, 10% van de wandbreedte)`. Een correcte kleine opstelling werd daardoor afgewezen.

## Gedrag na aanpassing

| Projectinstelling | Benodigde opnamen |
| --- | --- |
| Afmetingen bekend | Eén tag op elk van twee aangrenzende zijwanden, plus één boventag |
| Afmetingen bepalen | Eén tag op elk van vier zijwanden, plus één boventag; zijwandhoogte blijft ingevuld |

Meer tags blijven toegestaan zonder extra modus of instelling. Er geldt geen minimale horizontale afstand tussen tags om te mogen doorgaan. De UI noemt één tag per wand en vermeldt in de preview dat extra tags meer onderlinge controle bieden.

Een stabiele tag levert de normaalrichting en positie van zijn wandvlak. De gezamenlijke wandfit bepaalt nog steeds één rechthoekig tankframe. Korte verbindingslijnen tussen twee tagmiddens worden niet als extra beginschatting voor de horizontale richting gebruikt, omdat kleine positieruis daarop een grote hoekfout kan geven. Hun overige metingen blijven meetellen.

De eisen aan de benodigde vlakken blijven gelden, ook na uitsluiting. Eén enkele wand of alleen twee tegenoverliggende zijwanden bepaalt geen complete horizontale plaatsing. Verkeerde normaalrichtingen, conflicten, dubbele ID's en buitenliggende tags blijven onder de bestaande controles vallen. De opnamekwaliteit (meerdere verse beelden, spreiding, trackingframe, tagmaat) is niet versoepeld. Projectcoördinaten, opgeslagen sensoren en de normale fusionroute zijn ongewijzigd.

## Verificatie

Twee regressietests faalden eerst op de oude solver: één tag per benodigde wand en een kast van 300 × 240 × 300 mm met 60 mm horizontale tagspreiding. Bewijs: `app/build/wall-single-before.txt` (2 tests, 2 fouten).

De tests controleren beide dimensiebronnen, de berekende afmetingen en het volledige tankframe. Negatieve gevallen controleren ontbrekende aangrenzende/opposite vlakken en een tegenstrijdige enige referentie op een wand. De state-test loopt van toewijzen en meerdere beelden verzamelen via contour/deksel oplossen naar opslaan en heropenen. De native OpenCV/fusiontest draait ook met één geleerde tag per vlak en herkent daarna zowel een zijtag als uitsluitend een boventag.

Resultaten worden hieronder ingevuld na uitvoering.

## Gewijzigde bestanden

- `ar/calibration/WallCalibrationSolver.kt`: één tag per benodigd vlak; afstand geen afwijzingsgrond; korte afstand niet als richtingseed.
- `ui/WorkflowWallCalibration.kt`: instructies en uitleg in de preview.
- `WallCalibrationTest.kt`, `WallWorkflowStateTest.kt`, `WallPosePipelineTest.kt`: regressies en ketencontroles.

## Telefooncontrole

Scan de kleine kast met de juiste zwarte tagmaat en één tag per benodigd vlak. Vergelijk contour en deksel met de fysieke randen, ook na bewegen en terugkijken. Eén tag per wand geeft minder mogelijkheden om een verplaatste of verkeerd gemeten referentie op die wand te herkennen. De testgeometrie is gecontroleerde invoer; fysieke AR-nauwkeurigheid is niet gemeten. Niet gepusht.
