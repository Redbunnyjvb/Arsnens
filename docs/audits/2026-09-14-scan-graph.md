# Scan, tagnetwerk en afronden — 14 september 2026

## Onderzoek en wijzigingen

De beschrijving uit ChatGPT is als hypothese gecontroleerd tegen de lokale code. De offset op de fysieke transformator is niet rechtstreeks gereproduceerd; er is geen fysieke camera-opname beschikbaar.

| Onderdeel | Bevinding en oplossing |
| --- | --- |
| Gedraaide tags | Draaien binnen het tagvlak werd al meegenomen. De solver projecteerde de opgeslagen tag echter op het ideale wandvlak en verwijderde kanteling uit dat vlak. De werkelijke centrumpositie en volledige rotatie blijven nu behouden; alleen de tankcontour is rechthoekig gefit. |
| Voorlopige tags | Een uitsluitend via ARCore opgenomen node kon als vaste referentie worden gebruikt en daardoor een latere directe koppeling blokkeren. Alleen het rechtstreeks aan de seed gekoppelde netwerk bepaalt nu relocalisatie. Losse nodes blijven voorlopig. |
| Directe koppeling | De optimizer keurde een goede directe edge af wanneer de voorlopige ARCore-positie meer dan 150 mm afweek. Initialisatie volgt nu eerst een directe verbindingsboom; de ARCore-prior van gekoppelde nodes trekt deze niet terug. Cycluscontrole en afwijzing van slechte edges blijven bestaan. |
| Contourprojectie | Een bestaande preview bleef in zijn oude runtime-transformatie staan wanneer de seed opnieuw werd gelokaliseerd. Preview en opgeslagen tags volgen nu dezelfde framecorrectie. In de definitieve preview wordt alleen uitlijning bijgewerkt, niet het opgenomen meetbewijs. |
| AR-overzicht | Opties voor opgenomen tags en tagverbindingen. Lijnen met pijlen aan beide kanten, groene gekoppelde tags, oranje voorlopige tags, rood bij conflict/afgewezen verbinding; dubbele ring rond de seed. Geen tekst naast AR-referentietags. |
| Afronden | Definitief accepteren vereist dat alle gebruikte tags aan de seed gekoppeld zijn en dat de top direct aan een zijde gekoppeld is. Ontbrekende koppelingen krijgen een korte, uitvoerbare melding. |
| Meetsessie | Na acceptatie opent het project direct de sessiestart. Eén expliciete knop start de sessie en opent de camera; er wordt geen sessie stilzwijgend aangemaakt. |
| Sensorcapture | Een plots verplaatste sensor kan niet meer met het oudere stabiele samplecluster worden vastgelegd. |

## Betekenis van de weergave

- Groen: opgenomen tag met een gecontroleerd direct pad naar de seed; geen garantie op absolute millimeternauwkeurigheid.
- Oranje: opgenomen maar nog niet direct verbonden, of nog bezig met opnemen. Bekijk hem tegelijk met een groene referentietag.
- Rood: tegenstrijdige zichtbare referenties of afgewezen verbinding. Bekijk andere bekende referenties en controleer de tag via Opties.
- Seed: dubbele ring. De seed hoeft niet bij elke nieuwe tag zichtbaar te blijven; een keten van overlappende referenties volstaat.

Een sterke verbinding vraagt minimaal acht onafhankelijke bruikbare beelden en 700 ms overlap. Kleine tags kunnen langer nodig hebben. Zonder overlap kan ARCore een voorlopige plaats leveren, maar geen geverifieerde directe verbinding. Een bekende fysiek verplaatste tag wordt niet automatisch nieuwe waarheid.

## Offset: praktische interpretatie

De zwarte tagmaat moet kloppen, inclusief de juiste eenheid en zonder witte papierrand. Een verkeerde maat schaalt de poseafstand. Een verhoogd tagvlak vraagt een expliciete dekseloffset. De voorlopige grondcontour ligt op de hoogte van de zijtags totdat de bovenkant is gekoppeld. Draaien van het papier binnen zijn vlak is toegestaan; een tag moet wel vlak en stevig op de bedoelde wand zitten. Lichte kanteling wordt bewaard, sterke afwijking kan terecht tot afwijzing van de wandfit leiden.

Opgeslagen tagomtrekken en actuele detecties worden nu samen zichtbaar: verschil daartussen helpt onderscheid maken tussen netwerkmismatch en de rechthoekige contourfit. Na opnieuw lokaliseren mogen beide niet alleen door een oude preview-transformatie uit elkaar lopen.

## Validatie

Nieuwe regressies controleren een keten Voor → Boven → Links → Achter → Voor zonder permanente zichtbaarheid van de seed, koppeling van een voorlopige node met 350 mm geïnjecteerde ARCore-drift, volledige tagrotatie door scan/opslag/herladen/PnP, het meebewegen van de contourpreview met relocalisatie en de expliciete sessiestart na scannen.

Build-, testresultaten en screenshotbeoordeling worden hieronder bijgewerkt na de eindcontrole. De volledige fysieke S25 Ultra Definition of Done uit de overhaul blijft apart: afstand, licht, beweging, rondlopen, terugkeer naar referenties en onafhankelijke maatcontrole.
