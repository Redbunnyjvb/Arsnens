# Scan, tagnetwerk en afronden — 14 september 2026

## Onderzoek en wijzigingen

De beschrijving uit ChatGPT is als hypothese gecontroleerd tegen de lokale code. De offset op de fysieke transformator is niet rechtstreeks gereproduceerd; er is geen fysieke camera-opname beschikbaar.

| Onderdeel | Bevinding en oplossing |
| --- | --- |
| Gedraaide tags | Draaien binnen het tagvlak werd al meegenomen. De solver projecteerde de opgeslagen tag echter op het ideale wandvlak en verwijderde kanteling uit dat vlak. De werkelijke centrumpositie en volledige rotatie blijven nu behouden; alleen de tankcontour is rechthoekig gefit. |
| ARCore-tags | Een via ARCore opgenomen node kon een latere betere directe koppeling blokkeren. Direct gekoppelde, goed leesbare referenties hebben nu voorkeur. ARCore-bruggen worden expliciet als zwakkere edges bewaard; zonder betere zichtbare referentie kan een opgeslagen ARCore-tag ook voor hervatten dienen. |
| Directe koppeling | De optimizer keurde een goede directe edge af wanneer de voorlopige ARCore-positie meer dan 150 mm afweek. Initialisatie volgt nu eerst een directe verbindingsboom; de ARCore-prior van gekoppelde nodes trekt deze niet terug. Cycluscontrole en afwijzing van slechte edges blijven bestaan. |
| Contourprojectie | Een bestaande preview bleef in zijn oude runtime-transformatie staan wanneer de seed opnieuw werd gelokaliseerd. Preview en opgeslagen tags volgen nu dezelfde framecorrectie. In de definitieve preview wordt alleen uitlijning bijgewerkt, niet het opgenomen meetbewijs. |
| AR-overzicht | Opties voor opgenomen tags en tagverbindingen. Directe lijnen groen, ARCore-bruggen oranje gestippeld, rood bij conflict; dubbele ring rond de seed. Alleen de actief gescande tag toont zijn ID. |
| Afronden | Volgens de aanvullende gebruikerswens mogen ARCore-koppelingen, inclusief de top, worden geaccepteerd. De afronding toont hoeveel tags direct en hoeveel via ARCore gekoppeld zijn; dat onderscheid blijft opgeslagen. |
| Meetsessie | Na acceptatie opent het project direct de sessiestart. Eén expliciete knop start de sessie en opent de camera; er wordt geen sessie stilzwijgend aangemaakt. |
| Sensorcapture | Een plots verplaatste sensor kan niet meer met het oudere stabiele samplecluster worden vastgelegd. |
| Cameraplaatsing | De primaire actie neemt de meting op, accepteert die en selecteert automatisch de volgende nog niet geplaatste sensor. Pijlen wijzigen alleen de selectie. Cameravoorbereiding is uit de bediening verwijderd; 2D blijft voor het sensorplan. |
| Verticale richting | De verticale richting komt van gravity, niet van de opdrukrotatie. Relocalisatie wordt op dezelfde seed-gravity gericht, met behoud van het gemeten tagcentrum; de solver ontvangt één gezamenlijke verticale richting. |
| Conflictcontrole | Vergelijkt uitlijningen bij de camera, niet bij een mogelijk ver weg gelegen scan-oorsprong. Een kleine hoekfout veroorzaakt daardoor geen grote fictieve translatiefout door die afstand. Kleine/slechte tags nemen de leiding niet over van een veel beter zichtbare tag. |
| Posecontinuïteit | Een kort bewaarde tagpose wordt met de huidige ARCore-camerabeweging voorspeld en alleen gebruikt om de dubbelzinnige vlakke PnP-oplossing te kiezen. Nieuwe meetwaarden komen nog steeds uit nieuwe beelden. Tagkeuze weegt leesbaarheid en schermcentrum mee; handmatig kiezen aan de rand blijft mogelijk. |
| Te weinig tags na uitsluiten | De melding komt uit de rechthoekige wandfit, niet uit de eis voor directe taglinks. Tags vallen af boven 20 mm vlakafwijking of 12° afwijking van de toegewezen wandnormaal. De fout noemt nu de wand en tag-ID; Opties toont per afgewezen tag beide afwijkingen naast Opnieuw meten. De grenzen zijn niet verruimd. |

## Betekenis van de weergave

- Groen: opgenomen tag met een gecontroleerd direct pad naar de seed; geen garantie op absolute millimeternauwkeurigheid.
- Oranje: opgenomen via ARCore, of nog bezig met opnemen. Gelijktijdig bekijken met een bekende tag kan een sterkere directe koppeling toevoegen, maar is niet verplicht voor afronden.
- Rood: tegenstrijdige zichtbare referenties of afgewezen verbinding. Bekijk andere bekende referenties en controleer de tag via Opties.
- Seed: dubbele ring. De seed hoeft niet bij elke nieuwe tag zichtbaar te blijven; een keten van overlappende referenties volstaat.

Een sterke directe verbinding vraagt minimaal acht onafhankelijke bruikbare beelden en 700 ms overlap. Kleine tags kunnen langer nodig hebben. Zonder overlap gebruikt de app expliciete ARCore-bruggen. Goede directe edges domineren die bruggen zodra ze beschikbaar zijn; beide soorten bewijs blijven bewaard. Een bekende fysiek verplaatste tag wordt niet automatisch nieuwe waarheid.

De aanvullende gebruikersinstructies van 14 september vervangen de oorspronkelijke eis dat alle geaccepteerde contourtags rechtstreeks gekoppeld moeten zijn, en vervangen de tweestaps camera-acceptatie door één bewuste plaatsactie. Passieve detectie schrijft nog steeds geen sensormeting.

## Offset: praktische interpretatie

De zwarte tagmaat moet kloppen, inclusief de juiste eenheid en zonder witte papierrand. Een verkeerde maat schaalt de poseafstand. Een verhoogd tagvlak vraagt een expliciete dekseloffset. De voorlopige grondcontour ligt op de hoogte van de zijtags totdat de bovenkant is gekoppeld. Draaien van het papier binnen zijn vlak is toegestaan; een tag moet wel vlak en stevig op de bedoelde wand zitten. Lichte kanteling wordt bewaard, sterke afwijking kan terecht tot afwijzing van de wandfit leiden.

Opgeslagen tagomtrekken en actuele detecties worden nu samen zichtbaar: verschil daartussen helpt onderscheid maken tussen netwerkmismatch en de rechthoekige contourfit. Na opnieuw lokaliseren mogen beide niet alleen door een oude preview-transformatie uit elkaar lopen.

De melding “Na uitsluiten blijven te weinig tags over” betekent dat ten minste één vereiste wand geen bruikbare tag meer heeft na de fit. Mogelijke oorzaken zijn een verkeerde wandtoewijzing, afwijkende vaste/STL-maten, posefouten of een niet-vlakke tag. Zonder de specifieke scan is niet vastgesteld welke oorzaak bij de gebruiker speelt. Twee aanvullende regressies onderscheiden een 100 mm verhoogde boventag van een gekantelde boventag: de diagnose toont respectievelijk positie- en hoekafwijking.

## Validatie

Nieuwe regressies controleren een keten Voor → Boven → Links → Achter → Voor zonder permanente zichtbaarheid van de seed, koppeling van een voorlopige node met 350 mm geïnjecteerde ARCore-drift, volledige tagrotatie door scan/opslag/herladen/PnP, het meebewegen van de contourpreview met relocalisatie en de expliciete sessiestart na scannen.

Build-, testresultaten en screenshotbeoordeling worden hieronder bijgewerkt na de eindcontrole. De volledige fysieke S25 Ultra Definition of Done uit de overhaul blijft apart: afstand, licht, beweging, rondlopen, terugkeer naar referenties en onafhankelijke maatcontrole.
