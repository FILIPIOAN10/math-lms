-- What a wrong answer sends the student to revise: "Dacă vrei să recapitulezi noțiunile despre simetrie și segmente",
-- with a link to the chapter. It sits on the chapter, not on the place in the paper, so it always describes the lesson
-- the link opens - also when a question is pointed at another chapter - and every question already written gets it.
-- The teacher edits it with the chapter. NULL = not written, the student then reads a general sentence.
ALTER TABLE en_chapters ADD COLUMN revision_topic VARCHAR(200);

UPDATE en_chapters c
SET revision_topic = seeded.topic
FROM (VALUES
    ('A1', 'ordinea operațiilor, paranteze și puteri'),
    ('A2', 'rapoarte, proporții și mărimi proporționale'),
    ('A3', 'procente și fracții dintr-un număr'),
    ('A4', 'numere întregi, opusul și modulul unui număr'),
    ('A5', 'mulțimi, intervale și divizibilitate'),
    ('A6', 'compararea numerelor reale, radicali, modul și medii'),
    ('A7', 'ecuații de gradul I'),
    ('A8', 'tabele, grafice și diagrame'),
    ('A9', 'probleme cu ecuații și sisteme de ecuații'),
    ('A10', 'formule de calcul prescurtat, descompunere în factori și expresii algebrice'),
    ('A11', 'funcția liniară și graficul ei'),
    ('A12', 'fracții ordinare, fracții zecimale și rotunjiri'),
    ('A13', 'metode aritmetice de rezolvare a problemelor'),
    ('A14', 'ecuații de gradul al II-lea'),
    ('A15', 'statistică și probabilități'),
    ('G1', 'simetrie și segmente'),
    ('G2', 'unghiuri și drepte paralele tăiate de o secantă'),
    ('G3', 'linii importante în triunghi, perimetrul și aria triunghiului'),
    ('G4', 'triunghiul dreptunghic, teorema lui Pitagora, teorema catetei, teorema înălțimii și trigonometrie'),
    ('G5', 'patrulatere, proprietățile, perimetrele și ariile lor'),
    ('G6', 'cerc, unghiuri în cerc, tangentă, lungimea cercului și aria discului'),
    ('G7', 'asemănarea triunghiurilor și teorema lui Thales'),
    ('G8', 'corpuri geometrice, arii și volume'),
    ('G9', 'drepte și plane, unghiuri și distanțe în spațiu'),
    ('G10', 'trunchiuri, secțiuni și desfășurări')
) AS seeded(code, topic)
WHERE c.code = seeded.code;

-- S II.6 also asks how many vertices, edges and faces a solid has; the page only said it for the tetrahedron. After what
-- the page already has, and never on a page a teacher emptied.
UPDATE en_chapters
SET notes = notes || E'\n\n' || $EN$Vârfuri, muchii și fețe
• Cubul și paralelipipedul dreptunghic: $8$ vârfuri, $12$ muchii, $6$ fețe.
• Prisma cu baza un poligon cu $n$ laturi: $2n$ vârfuri, $3n$ muchii, $n + 2$ fețe.
• Piramida cu baza un poligon cu $n$ laturi: $n + 1$ vârfuri, $2n$ muchii, $n + 1$ fețe.
• Tetraedrul: $4$ vârfuri, $6$ muchii, $4$ fețe.$EN$
WHERE code = 'G8' AND notes <> '';
