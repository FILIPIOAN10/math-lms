-- The EN programme published in Monitorul Oficial nr. 860 bis/14.X.2026 (Anexa 11) covers more than the 20 chapters of
-- V33, which came from four official papers. Five new chapters hold what was missing altogether, and the existing pages
-- get the smaller topics after their text and their examples. A starting point the teacher edits freely, like V35: a
-- code already in use keeps its chapter, a page keeps the text and the examples it has, and a page the teacher emptied
-- stays empty. The new chapters get no automatic link (en_chapter_slots): the programme says what is taught, not where
-- it lands in the paper.

INSERT INTO en_chapters (code, title, area, position)
SELECT n.code, n.title, n.area, (SELECT COALESCE(MAX(position), 0) FROM en_chapters) + n.step
FROM (VALUES
    ('A12', 'Fracții ordinare și zecimale: operații', 'ALGEBRA', 1),
    ('A13', 'Metode aritmetice de rezolvare a problemelor', 'ALGEBRA', 2),
    ('A14', 'Ecuații de gradul al II-lea', 'ALGEBRA', 3),
    ('A15', 'Statistică și probabilități', 'ALGEBRA', 4),
    ('G10', 'Trunchiuri, secțiuni și desfășurări', 'GEOMETRY', 5)
) AS n (code, title, area, step)
ON CONFLICT (code) DO NOTHING;

-- A12
UPDATE en_chapters SET notes = $EN$Fracții ordinare și zecimale: operații

Fracții ordinare
• $\frac{a}{b}$, cu $b \neq 0$: $a$ este numărătorul, $b$ este numitorul.
• Subunitară: $a < b$, de exemplu $\frac{2}{5}$. Echiunitară: $a = b$, valorează $1$. Supraunitară: $a > b$, de exemplu $\frac{7}{3}$.
• Scoaterea întregilor: $\frac{7}{3} = 2\frac{1}{3}$, pentru că $7 : 3 = 2$ rest $1$.
• Introducerea întregilor: $2\frac{1}{3} = \frac{2 \cdot 3 + 1}{3} = \frac{7}{3}$.

Amplificare și simplificare
• Amplificarea cu $k$: $\frac{a}{b} = \frac{a \cdot k}{b \cdot k}$. Simplificarea: împarți numărătorul și numitorul la același număr.
• Fracții echivalente: reprezintă același număr, de exemplu $\frac{2}{3} = \frac{4}{6}$.
• Fracție ireductibilă: nu se mai poate simplifica, adică numărătorul și numitorul sunt prime între ele.

Compararea fracțiilor
• Același numitor: mai mare e fracția cu numărătorul mai mare: $\frac{5}{7} > \frac{3}{7}$.
• Același numărător: mai mare e fracția cu numitorul mai mic: $\frac{3}{4} > \frac{3}{5}$.
• Altfel: le aduci la același numitor.

Operații
• Adunare și scădere: aduci la numitor comun (c.m.m.m.c. al numitorilor), apoi aduni numărătorii: $\frac{1}{4} + \frac{1}{6} = \frac{3}{12} + \frac{2}{12} = \frac{5}{12}$.
• Înmulțire: numărător cu numărător, numitor cu numitor: $\frac{a}{b} \cdot \frac{c}{d} = \frac{a \cdot c}{b \cdot d}$.
• Împărțire: înmulțești cu inversa fracției: $\frac{a}{b} : \frac{c}{d} = \frac{a}{b} \cdot \frac{d}{c}$.
• Putere: $\left(\frac{a}{b}\right)^n = \frac{a^n}{b^n}$.

Fracții zecimale
• Numitor putere a lui $10$: $\frac{7}{10} = 0{,}7$; $\frac{23}{100} = 0{,}23$; $\frac{5}{1000} = 0{,}005$.
• Din zecimală în ordinară: $0{,}35 = \frac{35}{100} = \frac{7}{20}$.
• Din ordinară în zecimală: împarți numărătorul la numitor: $\frac{3}{8} = 0{,}375$; $\frac{1}{3} = 0{,}333\ldots = 0{,}(3)$.
• Comparare: completezi cu zerouri până au același număr de zecimale: $0{,}5 = 0{,}50 > 0{,}45$.
• Rotunjirea la sutimi: te uiți la a treia zecimală; dacă e cel puțin $5$, crești sutimile cu $1$: $3{,}147 \approx 3{,}15$, iar $3{,}142 \approx 3{,}14$.

Operații cu fracții zecimale
• Adunare și scădere: scrii numerele unul sub altul, cu virgula sub virgulă.
• Înmulțire: înmulțești fără virgulă, apoi rezultatul are atâtea zecimale câte au factorii împreună: $1{,}2 \cdot 0{,}3 = 0{,}36$.
• Împărțire: muți virgula la ambele numere până când împărțitorul devine număr natural: $1{,}2 : 0{,}3 = 12 : 3 = 4$.

Atenție
• Nu aduni numitorii: $\frac{1}{2} + \frac{1}{3} = \frac{5}{6}$, nu $\frac{2}{5}$.
• Numitorul comun e necesar doar la adunare și scădere, nu la înmulțire.$EN$
WHERE code = 'A12' AND notes IS NULL;
INSERT INTO en_chapter_examples (chapter_id, position, statement, solution)
SELECT c.id, e.position, e.statement, e.solution
FROM en_chapters c
CROSS JOIN (VALUES
    (1, $EN$Calculează $\frac{2}{3} + \frac{3}{4} - \frac{5}{6}$.$EN$,
        $EN$Numitorul comun este c.m.m.m.c.$(3, 4, 6) = 12$.
$\frac{8}{12} + \frac{9}{12} - \frac{10}{12} = \frac{7}{12}$.$EN$),
    (2, $EN$Calculează $\frac{4}{9} \cdot \frac{3}{8} : \frac{5}{6}$.$EN$,
        $EN$Lucrăm de la stânga la dreapta: $\frac{4}{9} \cdot \frac{3}{8} = \frac{12}{72} = \frac{1}{6}$.
Împărțirea devine înmulțire cu inversa: $\frac{1}{6} \cdot \frac{6}{5} = \frac{1}{5}$.$EN$),
    (3, $EN$Scrie fracția $\frac{17}{5}$ cu întregi și ca fracție zecimală.$EN$,
        $EN$$17 : 5 = 3$ rest $2$, deci $\frac{17}{5} = 3\frac{2}{5}$.
Amplificăm cu $2$: $\frac{17}{5} = \frac{34}{10} = 3{,}4$.$EN$),
    (4, $EN$Ordonează crescător numerele $\frac{3}{5}$, $0{,}55$ și $\frac{2}{3}$.$EN$,
        $EN$Le scriem pe toate ca fracții zecimale: $\frac{3}{5} = 0{,}6$ și $\frac{2}{3} = 0{,}666\ldots$
$0{,}55 < 0{,}6 < 0{,}666\ldots$, adică $0{,}55 < \frac{3}{5} < \frac{2}{3}$.$EN$),
    (5, $EN$Calculează $2{,}5 \cdot 0{,}4 + 1{,}8 : 0{,}6$.$EN$,
        $EN$$2{,}5 \cdot 0{,}4 = 1$
$1{,}8 : 0{,}6 = 18 : 6 = 3$
$1 + 3 = 4$.$EN$)
) AS e (position, statement, solution)
WHERE c.code = 'A12'
  AND NOT EXISTS (SELECT 1 FROM en_chapter_examples x WHERE x.chapter_id = c.id);

-- A13
UPDATE en_chapters SET notes = $EN$Metode aritmetice de rezolvare a problemelor

Metoda reducerii la unitate
• Afli întâi cât valorează o singură unitate, apoi înmulțești.
• Exemplu: 5 caiete costă 20 de lei, deci un caiet costă 4 lei, iar 8 caiete costă 32 de lei.

Metoda comparației
• Ai două situații cu aceleași două mărimi, de exemplu caiete și pixuri.
• Aduci una dintre mărimi la aceeași valoare în ambele situații, apoi scazi. Diferența rămasă ține doar de cealaltă mărime.

Metoda figurativă
• Reprezinți numerele prin segmente și numeri părțile egale.
• Dacă știi suma $S$ și diferența $D$: numărul mic este $(S - D) : 2$, iar cel mare este $(S + D) : 2$.
• Dacă un număr este de $k$ ori mai mare decât celălalt, suma lor are $k + 1$ părți egale.

Metoda mersului invers
• Pornești de la rezultatul final și parcurgi pașii de la coadă la cap.
• Fiecare operație se transformă în opusa ei: adunarea devine scădere, înmulțirea devine împărțire.

Metoda falsei ipoteze
• Presupui că toate obiectele sunt de același fel și calculezi.
• Împarți diferența față de enunț la cât aduce în plus un obiect de celălalt fel. Rezultatul este numărul obiectelor de celălalt fel.

Cum recunoști metoda
• „Cât costă mai multe, dacă știi cât costă câteva” → reducerea la unitate.
• Două cumpărături diferite, cu același fel de produse → comparația.
• „De $k$ ori mai mare”, „cu $n$ mai mult” → metoda figurativă.
• „A cheltuit, apoi a primit, i-au rămas…” → mersul invers.
• Două feluri de obiecte și un total (capete și picioare) → falsa ipoteză.

Aceleași probleme se pot rezolva și cu ecuații (capitolul A9), dar metoda aritmetică e deseori mai scurtă. Transformă unitățile de măsură la fel înainte să calculezi.$EN$
WHERE code = 'A13' AND notes IS NULL;
INSERT INTO en_chapter_examples (chapter_id, position, statement, solution)
SELECT c.id, e.position, e.statement, e.solution
FROM en_chapters c
CROSS JOIN (VALUES
    (1, $EN$5 muncitori fac 200 de piese în 4 zile. Câte piese fac 3 muncitori în 6 zile, lucrând la fel de repede?$EN$,
        $EN$Reducem la unitate. Într-o zi, cei 5 muncitori fac $200 : 4 = 50$ de piese.
Un muncitor face într-o zi $50 : 5 = 10$ piese.
3 muncitori în 6 zile fac $3 \cdot 6 \cdot 10 = 180$ de piese.$EN$),
    (2, $EN$2 caiete și 3 pixuri costă 17 lei, iar 3 caiete și 2 pixuri costă 18 lei. Cât costă un caiet și cât un pix?$EN$,
        $EN$Aducem caietele la același număr: înmulțim prima situație cu $3$ și a doua cu $2$.
6 caiete și 9 pixuri costă $51$ de lei; 6 caiete și 4 pixuri costă $36$ de lei.
Diferența de $5$ pixuri costă $51 - 36 = 15$ lei, deci un pix costă $3$ lei.
Din prima situație: 2 caiete costă $17 - 9 = 8$ lei, deci un caiet costă $4$ lei.$EN$),
    (3, $EN$Ana și Mihai au împreună 48 de timbre. Mihai are de 3 ori mai multe timbre decât Ana. Câte timbre are fiecare?$EN$,
        $EN$Ana are o parte, Mihai are 3 părți de aceeași mărime, deci în total sunt $4$ părți.
O parte: $48 : 4 = 12$ timbre.
Ana are $12$ timbre, iar Mihai are $3 \cdot 12 = 36$ de timbre.$EN$),
    (4, $EN$Un elev a citit în prima zi jumătate dintr-o carte, în a doua zi o treime din rest, iar apoi i-au mai rămas 40 de pagini. Câte pagini are cartea?$EN$,
        $EN$Mergem invers, de la cele 40 de pagini.
După prima zi rămăsese un rest. A citit o treime din el, deci cele $40$ de pagini sunt două treimi din rest.
Restul: $40 : 2 \cdot 3 = 60$ de pagini.
Acest rest este jumătate din carte, deci cartea are $60 \cdot 2 = 120$ de pagini.
Verificare: $120 - 60 = 60$, apoi $60 - 20 = 40$.$EN$),
    (5, $EN$Într-o curte sunt găini și iepuri, în total 20 de capete și 56 de picioare. Câte găini și câți iepuri sunt?$EN$,
        $EN$Presupunem că toate animalele sunt găini. Atunci ar fi $20 \cdot 2 = 40$ de picioare.
Lipsesc $56 - 40 = 16$ picioare. Fiecare iepure are cu $2$ picioare mai mult decât o găină.
Iepuri: $16 : 2 = 8$. Găini: $20 - 8 = 12$.
Verificare: $8 \cdot 4 + 12 \cdot 2 = 32 + 24 = 56$.$EN$),
    (6, $EN$Un rezervor conține $1{,}5$ m³ de apă. S-au consumat $\frac{2}{5}$ din apă. Câți litri au rămas?$EN$,
        $EN$Transformăm: $1{,}5$ m³ $= 1500$ dm³ $= 1500$ litri.
S-au consumat $\frac{2}{5} \cdot 1500 = 600$ de litri.
Au rămas $1500 - 600 = 900$ de litri.$EN$)
) AS e (position, statement, solution)
WHERE c.code = 'A13'
  AND NOT EXISTS (SELECT 1 FROM en_chapter_examples x WHERE x.chapter_id = c.id);

-- A14
UPDATE en_chapters SET notes = $EN$Ecuații de gradul al II-lea

Forma generală: $ax^2 + bx + c = 0$, cu $a \neq 0$. O astfel de ecuație are cel mult două soluții reale.

Cazuri simple
• $x^2 = k$, cu $k > 0$: soluțiile sunt $\sqrt{k}$ și $-\sqrt{k}$. De exemplu, $x^2 = 9$ are soluțiile $3$ și $-3$.
• $x^2 = 0$ are o singură soluție, $x = 0$. Dacă $k < 0$, ecuația $x^2 = k$ nu are soluții reale.
• $ax^2 + bx = 0$: scoți factor comun, $x(ax + b) = 0$, deci $x = 0$ sau $x = -\frac{b}{a}$.

Rezolvarea prin descompunere în factori
• Un produs este $0$ doar dacă unul dintre factori este $0$: $(x - 2)(x + 5) = 0 \Rightarrow x = 2$ sau $x = -5$.
• Trinomul $x^2 + sx + p$: cauți două numere cu suma $s$ și produsul $p$.
• Formule de calcul prescurtat: $x^2 - 6x + 9 = (x - 3)^2$; $x^2 - 16 = (x - 4)(x + 4)$.

Formula generală
• Discriminantul: $\Delta = b^2 - 4ac$.
• Dacă $\Delta > 0$, sunt două soluții: $x_{1,2} = \frac{-b \pm \sqrt{\Delta}}{2a}$.
• Dacă $\Delta = 0$, este o singură soluție: $x = -\frac{b}{2a}$.
• Dacă $\Delta < 0$, nu sunt soluții reale.

Atenție
• Aduci întâi ecuația la forma $ax^2 + bx + c = 0$, cu toți termenii într-o parte.
• Nu împărți ecuația la $x$: pierzi soluția $x = 0$.
• La $x^2 - 5x + 6 = 0$ avem $b = -5$, deci $-b = 5$ și $b^2 = 25$.
• La o problemă cu text păstrezi doar soluțiile care au sens: o lungime nu poate fi negativă.$EN$
WHERE code = 'A14' AND notes IS NULL;
INSERT INTO en_chapter_examples (chapter_id, position, statement, solution)
SELECT c.id, e.position, e.statement, e.solution
FROM en_chapters c
CROSS JOIN (VALUES
    (1, $EN$Rezolvă ecuația $x^2 - 25 = 0$.$EN$,
        $EN$Descompunem: $(x - 5)(x + 5) = 0$.
$x = 5$ sau $x = -5$, deci $S = \{-5, 5\}$.$EN$),
    (2, $EN$Rezolvă ecuația $2x^2 - 6x = 0$.$EN$,
        $EN$Factor comun: $2x(x - 3) = 0$.
$x = 0$ sau $x = 3$, deci $S = \{0, 3\}$.$EN$),
    (3, $EN$Rezolvă ecuația $x^2 - 5x + 6 = 0$.$EN$,
        $EN$$a = 1$, $b = -5$, $c = 6$.
$\Delta = (-5)^2 - 4 \cdot 1 \cdot 6 = 25 - 24 = 1$.
$x_{1,2} = \frac{5 \pm 1}{2}$, deci $x_1 = 3$ și $x_2 = 2$.
(Sau prin descompunere: $x^2 - 5x + 6 = (x - 2)(x - 3)$.)$EN$),
    (4, $EN$Rezolvă ecuația $2x^2 + 3x - 2 = 0$.$EN$,
        $EN$$\Delta = 3^2 - 4 \cdot 2 \cdot (-2) = 9 + 16 = 25$.
$x_{1,2} = \frac{-3 \pm 5}{4}$, deci $x_1 = \frac{1}{2}$ și $x_2 = -2$.$EN$),
    (5, $EN$Câte soluții reale au ecuațiile $x^2 - 4x + 4 = 0$ și $x^2 + x + 1 = 0$?$EN$,
        $EN$Prima: $\Delta = 16 - 16 = 0$, deci are o singură soluție, $x = 2$.
A doua: $\Delta = 1 - 4 = -3 < 0$, deci nu are soluții reale.$EN$),
    (6, $EN$Un dreptunghi are lungimea cu $3$ cm mai mare decât lățimea și aria de $40$ cm². Aflați dimensiunile lui.$EN$,
        $EN$Fie $x$ lățimea. Atunci $x(x + 3) = 40$, adică $x^2 + 3x - 40 = 0$.
$\Delta = 9 + 160 = 169$, deci $x_{1,2} = \frac{-3 \pm 13}{2}$, adică $x_1 = 5$ și $x_2 = -8$.
O lungime nu poate fi negativă, deci lățimea este $5$ cm, iar lungimea $8$ cm.$EN$)
) AS e (position, statement, solution)
WHERE c.code = 'A14'
  AND NOT EXISTS (SELECT 1 FROM en_chapter_examples x WHERE x.chapter_id = c.id);

-- A15
UPDATE en_chapters SET notes = $EN$Statistică și probabilități

Frecvența
• Frecvența unei valori arată de câte ori apare valoarea în setul de date.
• Frecvența relativă este frecvența împărțită la numărul total de date; se scrie adesea ca procent.
• Suma tuturor frecvențelor este numărul total de date.

Indicatorii unui set de date
• Media: suma tuturor datelor împărțită la numărul lor.
• Mediana: valoarea din mijloc, după ce ai ordonat datele crescător. Dacă sunt un număr par de date, mediana este media celor două valori din mijloc.
• Modul: valoarea cu frecvența cea mai mare. Un set de date poate avea mai multe moduri.
• Amplitudinea: diferența dintre cea mai mare și cea mai mică valoare.

Reprezentări
• Graficul cu bare: pe orizontală valorile, pe verticală frecvențele.
• Graficul cu linii: puncte unite prin segmente, potrivit pentru o mărime care se schimbă în timp.
• Poligonul frecvențelor: pentru fiecare valoare marchezi un punct la înălțimea frecvenței ei și unești punctele cu segmente.
• Aceste reprezentări se pot face și într-un soft matematic sau într-un tabel de calcul.

Probabilități
• Probabilitatea unui eveniment $E$: $P(E) = \frac{\text{numărul cazurilor favorabile}}{\text{numărul cazurilor posibile}}$.
• Toate cazurile posibile trebuie să aibă aceeași șansă.
• $0 \le P(E) \le 1$. Evenimentul imposibil are probabilitatea $0$, iar evenimentul sigur are probabilitatea $1$.
• Probabilitatea ca $E$ să NU se întâmple este $1 - P(E)$.
• Rezultatul se poate scrie și ca procent: $\frac{1}{4} = 25\%$.

Atenție
• La mediană ordonezi datele întâi.
• Modul este valoarea, nu frecvența ei: dacă 8 elevi au nota 9, modul este 9, nu 8.
• În tabelele cu frecvențe, mediana se caută după poziție, ținând cont de câte ori apare fiecare valoare.
• Cazurile posibile le incluzi și pe cele favorabile.$EN$
WHERE code = 'A15' AND notes IS NULL;
INSERT INTO en_chapter_examples (chapter_id, position, statement, solution)
SELECT c.id, e.position, e.statement, e.solution
FROM en_chapters c
CROSS JOIN (VALUES
    (1, $EN$Notele a 10 elevi sunt: $8, 5, 7, 10, 6, 7, 9, 8, 7, 10$. Calculați media, mediana, modul și amplitudinea.$EN$,
        $EN$Ordonăm crescător: $5, 6, 7, 7, 7, 8, 8, 9, 10, 10$.
Media: $77 : 10 = 7{,}7$.
Sunt 10 date, deci mediana este media valorilor de pe locurile 5 și 6: $\frac{7 + 8}{2} = 7{,}5$.
Modul: $7$ (apare de 3 ori).
Amplitudinea: $10 - 5 = 5$.$EN$),
    (2, $EN$Temperaturile măsurate în 5 zile sunt: $12, 15, 9, 15, 14$ grade. Aflați media și mediana.$EN$,
        $EN$Ordonăm: $9, 12, 14, 15, 15$.
Media: $65 : 5 = 13$ grade.
Sunt 5 date, deci mediana este valoarea din mijloc, de pe locul 3: $14$ grade.$EN$),
    (3, $EN$Notele unei clase:
nota 6 — frecvența 2
nota 7 — frecvența 5
nota 8 — frecvența 8
nota 9 — frecvența 4
nota 10 — frecvența 1
Aflați numărul elevilor, frecvența relativă a notei 8, modul, mediana și media.$EN$,
        $EN$Numărul elevilor: $2 + 5 + 8 + 4 + 1 = 20$.
Frecvența relativă a notei 8: $\frac{8}{20} = 40\%$.
Modul: $8$, nota cu frecvența cea mai mare.
Mediana: locurile 10 și 11. Primele $2 + 5 = 7$ note sunt mai mici decât 8, iar locurile de la 8 la 15 au nota 8, deci mediana este $8$.
Media: $\frac{6 \cdot 2 + 7 \cdot 5 + 8 \cdot 8 + 9 \cdot 4 + 10 \cdot 1}{20} = \frac{157}{20} = 7{,}85$.$EN$),
    (4, $EN$Pentru tabelul de la modelul anterior, ce puncte unești ca să desenezi poligonul frecvențelor?$EN$,
        $EN$Pe orizontală pui nota, iar pe verticală frecvența ei.
Punctele sunt $(6, 2)$, $(7, 5)$, $(8, 8)$, $(9, 4)$, $(10, 1)$, unite în ordine prin segmente.
Punctul cel mai înalt este la nota 8, adică la mod.$EN$),
    (5, $EN$Se aruncă un zar. Care este probabilitatea să apară un număr prim?$EN$,
        $EN$Cazuri posibile: $6$ (numerele de la 1 la 6).
Cazuri favorabile: $2$, $3$, $5$, adică $3$ cazuri ($1$ nu este prim).
$P = \frac{3}{6} = \frac{1}{2} = 50\%$.$EN$),
    (6, $EN$Într-o cutie sunt 5 bile roșii, 7 bile albastre și 8 bile verzi. Se extrage o bilă. Care este probabilitatea ca ea să NU fie verde?$EN$,
        $EN$Cazuri posibile: $5 + 7 + 8 = 20$.
Bile care nu sunt verzi: $5 + 7 = 12$.
$P = \frac{12}{20} = \frac{3}{5} = 60\%$.
(Sau: $1 - \frac{8}{20} = \frac{3}{5}$.)$EN$),
    (7, $EN$Se alege la întâmplare un număr natural de două cifre. Care este probabilitatea ca el să fie divizibil cu 5?$EN$,
        $EN$Numere de două cifre: de la 10 la 99, adică $90$ de numere.
Divizibile cu 5: $10, 15, \ldots, 95$, adică $18$ numere.
$P = \frac{18}{90} = \frac{1}{5}$.$EN$)
) AS e (position, statement, solution)
WHERE c.code = 'A15'
  AND NOT EXISTS (SELECT 1 FROM en_chapter_examples x WHERE x.chapter_id = c.id);

-- G10
UPDATE en_chapters SET notes = $EN$Trunchiuri, secțiuni și desfășurări

Secțiuni paralele cu baza
• Un plan paralel cu baza taie o piramidă sau un con după o figură asemenea cu baza.
• Dacă planul este la distanța $d$ de vârf, iar corpul are înălțimea $h$, raportul de asemănare este $k = \frac{d}{h}$.
• Raportul ariilor este $k^2$, iar raportul volumelor este $k^3$.

Secțiuni diagonale și axiale
• Secțiunea diagonală a unei prisme patrulatere drepte este un dreptunghi: o latură este diagonala bazei, cealaltă este înălțimea prismei.
• Secțiunea diagonală a unei piramide patrulatere regulate este un triunghi isoscel: baza lui este diagonala bazei, iar înălțimea lui este înălțimea piramidei.
• Secțiunea axială a unui cilindru este un dreptunghi cu laturile $2R$ și $G$.
• Secțiunea axială a unui con este un triunghi isoscel cu baza $2R$, laturile congruente $G$ și înălțimea $h$.

Trunchiul de piramidă regulată
Notații: $A_B$ și $A_b$ ariile bazelor, $P_B$ și $P_b$ perimetrele lor, $h$ înălțimea, $a_t$ apotema trunchiului (înălțimea unei fețe laterale).
• Fețele laterale sunt trapeze isoscele.
• $A_l = \frac{(P_B + P_b) \cdot a_t}{2}$
• $A_t = A_l + A_B + A_b$
• $V = \frac{h}{3}\left(A_B + A_b + \sqrt{A_B \cdot A_b}\right)$
• Legătura: $a_t^2 = h^2 + (a_B - a_b)^2$, unde $a_B$ și $a_b$ sunt apotemele bazelor.

Trunchiul de con circular drept
Notații: $R$ și $r$ razele bazelor, $G$ generatoarea, $h$ înălțimea.
• Secțiunea axială este un trapez isoscel cu bazele $2R$ și $2r$.
• $G^2 = h^2 + (R - r)^2$
• $A_l = \pi G(R + r)$
• $A_t = \pi G(R + r) + \pi R^2 + \pi r^2$
• $V = \frac{\pi h}{3}\left(R^2 + r^2 + Rr\right)$

Desfășurări
• Prisma dreaptă: fețele laterale formează un dreptunghi cu laturile $P_b$ și $h$.
• Cilindrul: suprafața laterală este un dreptunghi cu laturile $2\pi R$ și $G$.
• Conul: suprafața laterală este un sector de cerc cu raza $G$; unghiul lui la centru este $\frac{R}{G} \cdot 360^\circ$.
• Cel mai scurt drum pe suprafața unui corp: desfășori fețele în plan și unești cele două puncte printr-un segment.

Atenție
• Înălțimea $h$ este în interiorul corpului, iar apotema $a_t$ și generatoarea $G$ sunt pe suprafață. Le leagă teorema lui Pitagora, într-un trapez dreptunghic.
• Raportul volumelor este $k^3$, nu $k^2$.
• Volumul trunchiului se poate afla și ca diferență: corpul întreg minus corpul mic tăiat de la vârf.$EN$
WHERE code = 'G10' AND notes IS NULL;
INSERT INTO en_chapter_examples (chapter_id, position, statement, solution)
SELECT c.id, e.position, e.statement, e.solution
FROM en_chapters c
CROSS JOIN (VALUES
    (1, $EN$Un trunchi de piramidă patrulateră regulată are laturile bazelor de $10$ cm și $4$ cm și înălțimea de $4$ cm. Calculați apotema, aria laterală și volumul.$EN$,
        $EN$Apotemele bazelor: $a_B = 5$ cm și $a_b = 2$ cm.
$a_t^2 = 4^2 + (5 - 2)^2 = 16 + 9 = 25$, deci $a_t = 5$ cm.
$P_B = 40$ cm, $P_b = 16$ cm, deci $A_l = \frac{(40 + 16) \cdot 5}{2} = 140$ cm².
$A_B = 100$ cm², $A_b = 16$ cm², deci $\sqrt{A_B \cdot A_b} = 40$.
$V = \frac{4}{3}(100 + 16 + 40) = \frac{4}{3} \cdot 156 = 208$ cm³.$EN$),
    (2, $EN$Un trunchi de con are razele de $7$ cm și $4$ cm și înălțimea de $4$ cm. Calculați generatoarea, aria laterală și volumul.$EN$,
        $EN$$G^2 = 4^2 + (7 - 4)^2 = 25$, deci $G = 5$ cm.
$A_l = \pi \cdot 5 \cdot (7 + 4) = 55\pi$ cm².
$V = \frac{\pi \cdot 4}{3}(49 + 16 + 28) = \frac{4\pi}{3} \cdot 93 = 124\pi$ cm³.$EN$),
    (3, $EN$O piramidă are aria bazei de $72$ cm² și înălțimea de $12$ cm. Un plan paralel cu baza o taie la $6$ cm de vârf. Calculați aria secțiunii și volumul trunchiului rămas.$EN$,
        $EN$Raportul de asemănare: $k = \frac{6}{12} = \frac{1}{2}$.
Aria secțiunii: $72 \cdot \frac{1}{4} = 18$ cm².
Volumul piramidei: $\frac{72 \cdot 12}{3} = 288$ cm³.
Volumul piramidei mici: $288 \cdot \frac{1}{8} = 36$ cm³.
Volumul trunchiului: $288 - 36 = 252$ cm³.$EN$),
    (4, $EN$Calculați ariile secțiunilor: a) axială într-un con cu $R = 6$ cm și $h = 8$ cm; b) axială într-un cilindru cu $R = 3$ cm și $G = 10$ cm; c) diagonală într-un cub cu muchia de $4$ cm.$EN$,
        $EN$a) Triunghi isoscel cu baza $12$ cm și înălțimea $8$ cm: aria $= \frac{12 \cdot 8}{2} = 48$ cm².
b) Dreptunghi cu laturile $6$ cm și $10$ cm: aria $= 60$ cm².
c) Dreptunghi cu laturile $4$ cm și $4\sqrt{2}$ cm: aria $= 16\sqrt{2}$ cm².$EN$),
    (5, $EN$Un con are raza de $3$ cm și generatoarea de $12$ cm. Ce măsură are unghiul sectorului de cerc obținut prin desfășurarea suprafeței laterale?$EN$,
        $EN$Unghiul este $\frac{R}{G} \cdot 360^\circ = \frac{3}{12} \cdot 360^\circ = 90^\circ$.$EN$),
    (6, $EN$Pe fețele cubului $ABCDA'B'C'D'$ cu muchia de $6$ cm, o furnică merge din $A$ în $C'$ traversând muchia $BB'$. Care este cel mai scurt drum?$EN$,
        $EN$Desfășurăm fețele $ABB'A'$ și $BCC'B'$ în același plan: ele formează un dreptunghi cu laturile $12$ cm și $6$ cm.
Cel mai scurt drum este diagonala acestui dreptunghi:
$AC' = \sqrt{12^2 + 6^2} = \sqrt{180} = 6\sqrt{5}$ cm.$EN$)
) AS e (position, statement, solution)
WHERE c.code = 'G10'
  AND NOT EXISTS (SELECT 1 FROM en_chapter_examples x WHERE x.chapter_id = c.id);

-- What the existing pages were missing: after the text a page has, and after the examples it has.

-- A1
UPDATE en_chapters SET notes = notes || E'\n\n' || $EN$Împărțirea cu rest
• $D = I \cdot C + R$, cu $R < I$. Exemplu: $47 : 5 = 9$ rest $2$, pentru că $47 = 5 \cdot 9 + 2$.

Factor comun
• $a \cdot b + a \cdot c = a \cdot (b + c)$. Exemplu: $17 \cdot 23 + 17 \cdot 77 = 17 \cdot 100 = 1700$.

Compararea puterilor
• Aceeași bază: mai mare e puterea cu exponentul mai mare: $2^{30} > 2^{20}$.
• Același exponent: mai mare e puterea cu baza mai mare: $3^{10} > 2^{10}$.
• Altfel, încerci să le aduci la aceeași bază sau la același exponent.

Scrierea în baza 10 și în baza 2
• Baza 10: fiecare cifră se înmulțește cu o putere a lui $10$: $507 = 5 \cdot 10^2 + 0 \cdot 10 + 7$.
• Baza 2 folosește doar cifrele $0$ și $1$, iar fiecare cifră se înmulțește cu o putere a lui $2$: $1011_{(2)} = 1 \cdot 2^3 + 0 \cdot 2^2 + 1 \cdot 2 + 1 = 11$.

Aproximări și estimări
• Rotunjirea la zeci: dacă cifra unităților este cel puțin $5$, rotunjești în sus: $347 \approx 350$, iar $342 \approx 340$.
• Estimarea verifică rapid un calcul: $398 + 203 \approx 400 + 200 = 600$.$EN$
WHERE code = 'A1' AND notes <> '';
INSERT INTO en_chapter_examples (chapter_id, position, statement, solution)
SELECT c.id, last.position + e.position, e.statement, e.solution
FROM en_chapters c
CROSS JOIN LATERAL (SELECT MAX(x.position) AS position FROM en_chapter_examples x WHERE x.chapter_id = c.id) last
CROSS JOIN (VALUES
    (1, $EN$La o împărțire, împărțitorul este $7$, câtul este $12$, iar restul este $5$. Aflați deîmpărțitul.$EN$,
        $EN$$D = I \cdot C + R = 7 \cdot 12 + 5 = 84 + 5 = 89$.$EN$),
    (2, $EN$Comparați numerele $2^{30}$ și $3^{20}$.$EN$,
        $EN$Aducem la același exponent: $2^{30} = (2^3)^{10} = 8^{10}$ și $3^{20} = (3^2)^{10} = 9^{10}$.
$8 < 9$, deci $8^{10} < 9^{10}$, adică $2^{30} < 3^{20}$.$EN$),
    (3, $EN$Scrieți numărul $13$ în baza 2 și numărul $10110_{(2)}$ în baza 10.$EN$,
        $EN$$13 = 8 + 4 + 1 = 1 \cdot 2^3 + 1 \cdot 2^2 + 0 \cdot 2 + 1$, deci $13 = 1101_{(2)}$.
$10110_{(2)} = 2^4 + 2^2 + 2 = 16 + 4 + 2 = 22$.$EN$)
) AS e (position, statement, solution)
WHERE c.code = 'A1' AND last.position IS NOT NULL;

-- A2
UPDATE en_chapters SET notes = notes || E'\n\n' || $EN$Proporții derivate
• Cu aceiași termeni: din $\frac{a}{b} = \frac{c}{d}$ obții $\frac{b}{a} = \frac{d}{c}$, $\frac{a}{c} = \frac{b}{d}$ și $\frac{d}{b} = \frac{c}{a}$.
• Cu alți termeni: $\frac{a + b}{b} = \frac{c + d}{d}$, $\frac{a - b}{b} = \frac{c - d}{d}$ și $\frac{a}{a + b} = \frac{c}{c + d}$.

Regula de trei simplă
• Scrii pe un rând valorile cunoscute, iar sub ele valoarea dată și necunoscuta $x$.
• Întreabă-te întâi: când una dintre mărimi crește, cealaltă crește sau scade?
• Mărimi direct proporționale: dacă $a$ îi corespunde lui $b$, atunci lui $c$ îi corespunde $x = \frac{b \cdot c}{a}$.
• Mărimi invers proporționale: $x = \frac{a \cdot b}{c}$.$EN$
WHERE code = 'A2' AND notes <> '';
INSERT INTO en_chapter_examples (chapter_id, position, statement, solution)
SELECT c.id, last.position + e.position, e.statement, e.solution
FROM en_chapters c
CROSS JOIN LATERAL (SELECT MAX(x.position) AS position FROM en_chapter_examples x WHERE x.chapter_id = c.id) last
CROSS JOIN (VALUES
    (1, $EN$4 kg de mere costă 18 lei. Cât costă 6 kg de mere?$EN$,
        $EN$Regula de trei simplă, mărimi direct proporționale:
4 kg ... 18 lei
6 kg ... $x$ lei
$x = \frac{6 \cdot 18}{4} = 27$ de lei.$EN$),
    (2, $EN$Dacă $\frac{a}{b} = \frac{3}{4}$, calculați $\frac{a + b}{b}$.$EN$,
        $EN$Proporția derivată: $\frac{a + b}{b} = \frac{3 + 4}{4} = \frac{7}{4}$.$EN$)
) AS e (position, statement, solution)
WHERE c.code = 'A2' AND last.position IS NOT NULL;

-- A5
UPDATE en_chapters SET notes = notes || E'\n\n' || $EN$Mulțimi definite printr-o proprietate
• $A = \{x \in \mathbb{N} \mid x < 4\}$ înseamnă: numerele naturale mai mici decât $4$, adică $A = \{0, 1, 2, 3\}$.

Cardinalul unei mulțimi
• Cardinalul unei mulțimi finite este numărul elementelor ei: pentru $A = \{2, 4, 6\}$ avem $\operatorname{card} A = 3$.
• Mulțimea vidă $\emptyset$ nu are elemente. Mulțimile $\mathbb{N}$ și $\mathbb{Z}$ sunt infinite.

Numere compuse, numere prime între ele
• Număr compus: are mai mult de doi divizori, de exemplu $4$, $6$, $9$.
• Numere prime între ele: singurul lor divizor comun este $1$. De exemplu, $8$ și $15$ sunt prime între ele, deși niciunul nu e prim.
• Criteriul cu $10^n$: numărul se termină cu cel puțin $n$ zerouri.

Proprietăți ale divizibilității
• $a \mid a$.
• Dacă $a \mid b$ și $b \mid c$, atunci $a \mid c$.
• Dacă $a \mid b$ și $a \mid c$, atunci $a \mid (b + c)$ și, pentru $b \ge c$, $a \mid (b - c)$.
• Dacă $a \mid b \cdot c$, iar $a$ și $b$ sunt prime între ele, atunci $a \mid c$.$EN$
WHERE code = 'A5' AND notes <> '';
INSERT INTO en_chapter_examples (chapter_id, position, statement, solution)
SELECT c.id, last.position + e.position, e.statement, e.solution
FROM en_chapters c
CROSS JOIN LATERAL (SELECT MAX(x.position) AS position FROM en_chapter_examples x WHERE x.chapter_id = c.id) last
CROSS JOIN (VALUES
    (1, $EN$Scrieți elementele mulțimii $A = \{x \in \mathbb{N} \mid 2x - 1 \le 7\}$ și aflați cardinalul ei.$EN$,
        $EN$$2x \le 8 \Rightarrow x \le 4$, iar $x$ este număr natural.
$A = \{0, 1, 2, 3, 4\}$, deci $\operatorname{card} A = 5$.$EN$),
    (2, $EN$Aflați numerele naturale $n$ pentru care $(n + 1) \mid (n + 4)$.$EN$,
        $EN$$n + 4 = (n + 1) + 3$, iar $n + 1$ se divide cu el însuși, deci trebuie ca $(n + 1) \mid 3$.
$n + 1 \in \{1, 3\}$, deci $n \in \{0, 2\}$.$EN$)
) AS e (position, statement, solution)
WHERE c.code = 'A5' AND last.position IS NOT NULL;

-- A6
UPDATE en_chapters SET notes = notes || E'\n\n' || $EN$Puteri cu exponent întreg negativ
• Pentru $a \neq 0$: $a^{-n} = \frac{1}{a^n}$. Exemple: $2^{-3} = \frac{1}{8}$; $\left(\frac{2}{3}\right)^{-2} = \left(\frac{3}{2}\right)^2 = \frac{9}{4}$.
• Regulile de calcul cu puteri rămân aceleași.

Mulțimile de numere
• $\mathbb{N} \subset \mathbb{Z} \subset \mathbb{Q} \subset \mathbb{R}$.
• Numerele iraționale, ca $\sqrt{2}$ sau $\pi$, au o infinitate de zecimale care nu se repetă periodic.
• Atenție: $\sqrt{16} = 4$ este număr natural, deci un radical nu este neapărat irațional.

Estimarea rădăcinii pătrate
• Încadrezi numărul între două pătrate perfecte: $9 < 10 < 16 \Rightarrow 3 < \sqrt{10} < 4$.
• La zecimi: $3{,}1^2 = 9{,}61$ și $3{,}2^2 = 10{,}24$, deci $3{,}1 < \sqrt{10} < 3{,}2$.

Media ponderată și media geometrică
• Media aritmetică ponderată: $m_p = \frac{a_1 p_1 + a_2 p_2 + \ldots + a_n p_n}{p_1 + p_2 + \ldots + p_n}$, unde $p_1, \ldots, p_n$ sunt ponderile.
• Media geometrică a două numere pozitive: $m_g = \sqrt{a \cdot b}$.$EN$
WHERE code = 'A6' AND notes <> '';
INSERT INTO en_chapter_examples (chapter_id, position, statement, solution)
SELECT c.id, last.position + e.position, e.statement, e.solution
FROM en_chapters c
CROSS JOIN LATERAL (SELECT MAX(x.position) AS position FROM en_chapter_examples x WHERE x.chapter_id = c.id) last
CROSS JOIN (VALUES
    (1, $EN$Un elev are de trei ori nota 8 și de două ori nota 10. Calculați media ponderată a notelor.$EN$,
        $EN$$m_p = \frac{8 \cdot 3 + 10 \cdot 2}{3 + 2} = \frac{24 + 20}{5} = \frac{44}{5} = 8{,}8$.$EN$),
    (2, $EN$Calculați media geometrică a numerelor $3 - \sqrt{5}$ și $3 + \sqrt{5}$.$EN$,
        $EN$$m_g = \sqrt{(3 - \sqrt{5})(3 + \sqrt{5})} = \sqrt{9 - 5} = \sqrt{4} = 2$.$EN$),
    (3, $EN$Între ce numere naturale consecutive se află $\sqrt{40}$?$EN$,
        $EN$$36 < 40 < 49$, deci $6 < \sqrt{40} < 7$.$EN$),
    (4, $EN$Calculați $2^{-2} + \left(\frac{1}{3}\right)^{-1}$.$EN$,
        $EN$$2^{-2} = \frac{1}{4}$ și $\left(\frac{1}{3}\right)^{-1} = 3$.
$\frac{1}{4} + 3 = \frac{13}{4}$.$EN$)
) AS e (position, statement, solution)
WHERE c.code = 'A6' AND last.position IS NOT NULL;

-- A7
UPDATE en_chapters SET notes = notes || E'\n\n' || $EN$Soluțiile unei ecuații
• Mulțimea soluțiilor: $S = \{4\}$ înseamnă că singura soluție este $4$.
• Ecuații echivalente: au aceeași mulțime de soluții.
• Dacă aduni același număr în ambele părți sau înmulțești ambele părți cu același număr nenul, obții o ecuație echivalentă.
• Dacă, după reducere, rămâne $0 \cdot x = 0$, orice număr este soluție: egalitatea este o identitate. Dacă rămâne $0 \cdot x = 5$, ecuația nu are soluții.$EN$
WHERE code = 'A7' AND notes <> '';
INSERT INTO en_chapter_examples (chapter_id, position, statement, solution)
SELECT c.id, last.position + e.position, e.statement, e.solution
FROM en_chapters c
CROSS JOIN LATERAL (SELECT MAX(x.position) AS position FROM en_chapter_examples x WHERE x.chapter_id = c.id) last
CROSS JOIN (VALUES
    (1, $EN$Rezolvă ecuațiile $3(x + 1) - 3x = 3$ și $2(x + 1) = 2x + 5$.$EN$,
        $EN$Prima: $3x + 3 - 3x = 3$, adică $3 = 3$. Egalitatea e adevărată pentru orice $x$, deci $S = \mathbb{R}$.
A doua: $2x + 2 = 2x + 5$, adică $2 = 5$, ceea ce e fals, deci $S = \emptyset$.$EN$)
) AS e (position, statement, solution)
WHERE c.code = 'A7' AND last.position IS NOT NULL;

-- A9
UPDATE en_chapters SET notes = notes || E'\n\n' || $EN$Sisteme de două ecuații: cele două metode
• Substituția: scoți o necunoscută dintr-o ecuație și o înlocuiești în cealaltă.
• Reducerea: înmulțești ecuațiile astfel încât, prin adunare sau scădere, o necunoscută să dispară.
• Soluția este o pereche $(x, y)$. O verifici în ambele ecuații.$EN$
WHERE code = 'A9' AND notes <> '';
INSERT INTO en_chapter_examples (chapter_id, position, statement, solution)
SELECT c.id, last.position + e.position, e.statement, e.solution
FROM en_chapters c
CROSS JOIN LATERAL (SELECT MAX(x.position) AS position FROM en_chapter_examples x WHERE x.chapter_id = c.id) last
CROSS JOIN (VALUES
    (1, $EN$Rezolvă sistemul format din ecuațiile $x + 2y = 8$ și $3x - y = 3$, prin ambele metode.$EN$,
        $EN$Substituție: din prima, $x = 8 - 2y$. Înlocuim în a doua:
$3(8 - 2y) - y = 3 \Rightarrow 24 - 7y = 3 \Rightarrow y = 3$, iar $x = 8 - 6 = 2$.
Reducere: înmulțim a doua ecuație cu $2$: $6x - 2y = 6$. O adunăm cu prima:
$7x = 14 \Rightarrow x = 2$, apoi $y = 3$.
Verificare: $2 + 6 = 8$ și $6 - 3 = 3$.$EN$)
) AS e (position, statement, solution)
WHERE c.code = 'A9' AND last.position IS NOT NULL;

-- A10
UPDATE en_chapters SET notes = notes || E'\n\n' || $EN$Reducerea termenilor asemenea
• Termenii asemenea au aceeași parte cu litere, de exemplu $3x^2$ și $-5x^2$. Aduni doar coeficienții: $3x^2 + 2x - 5x^2 = -2x^2 + 2x$.

Descompunerea prin grupare de termeni
• Grupezi termenii câte doi, scoți factor comun din fiecare grup, apoi scoți factorul care s-a repetat: $ab + ac + 3b + 3c = a(b + c) + 3(b + c) = (b + c)(a + 3)$.$EN$
WHERE code = 'A10' AND notes <> '';
INSERT INTO en_chapter_examples (chapter_id, position, statement, solution)
SELECT c.id, last.position + e.position, e.statement, e.solution
FROM en_chapters c
CROSS JOIN LATERAL (SELECT MAX(x.position) AS position FROM en_chapter_examples x WHERE x.chapter_id = c.id) last
CROSS JOIN (VALUES
    (1, $EN$Descompune în factori expresia $x^3 + 2x^2 - 9x - 18$.$EN$,
        $EN$Grupăm primii doi și ultimii doi termeni:
$x^2(x + 2) - 9(x + 2) = (x + 2)(x^2 - 9)$.
Folosim diferența de pătrate: $(x + 2)(x - 3)(x + 3)$.$EN$)
) AS e (position, statement, solution)
WHERE c.code = 'A10' AND last.position IS NOT NULL;

-- A11
UPDATE en_chapters SET notes = notes || E'\n\n' || $EN$Produsul cartezian
• $A \times B$ este mulțimea perechilor $(a, b)$ cu $a \in A$ și $b \in B$.
• Ordinea contează: $(1, 2) \neq (2, 1)$.
• Dacă $A$ are $m$ elemente și $B$ are $n$ elemente, $A \times B$ are $m \cdot n$ perechi.

Funcții pe o mulțime finită sau pe un interval
• O funcție $f : D \to \mathbb{R}$ asociază fiecărui element din $D$ un singur număr. Poate fi dată printr-o diagramă, un tabel sau o formulă.
• Dacă $D$ este o mulțime finită, graficul este format din puncte izolate: pentru $f : \{0, 1, 2\} \to \mathbb{R}$, $f(x) = 2x$, punctele sunt $(0, 0)$, $(1, 2)$ și $(2, 4)$.
• Dacă $D$ este un interval $[m, n]$, graficul lui $f(x) = ax + b$ este un segment, cu capetele în punctele de abscise $m$ și $n$.$EN$
WHERE code = 'A11' AND notes <> '';
INSERT INTO en_chapter_examples (chapter_id, position, statement, solution)
SELECT c.id, last.position + e.position, e.statement, e.solution
FROM en_chapters c
CROSS JOIN LATERAL (SELECT MAX(x.position) AS position FROM en_chapter_examples x WHERE x.chapter_id = c.id) last
CROSS JOIN (VALUES
    (1, $EN$Fie $A = \{1, 2\}$ și $B = \{3, 4, 5\}$. Scrieți $A \times B$.$EN$,
        $EN$$A \times B = \{(1, 3), (1, 4), (1, 5), (2, 3), (2, 4), (2, 5)\}$.
Sunt $2 \cdot 3 = 6$ perechi.$EN$),
    (2, $EN$Cum arată graficul funcției $f : [0, 2] \to \mathbb{R}$, $f(x) = x + 1$?$EN$,
        $EN$$f(0) = 1$ și $f(2) = 3$.
Graficul este segmentul care unește punctele $A(0, 1)$ și $B(2, 3)$.$EN$)
) AS e (position, statement, solution)
WHERE c.code = 'A11' AND last.position IS NOT NULL;

-- G1
UPDATE en_chapters SET notes = notes || E'\n\n' || $EN$Puncte și drepte
• Prin două puncte distincte trece o dreaptă și numai una.
• Un punct se află pe o dreaptă sau în afara ei.
• Două drepte distincte dintr-un plan sunt concurente (au un punct comun) sau paralele (nu au niciun punct comun).

Unități de măsură
• Lungime: $1$ km $= 1000$ m; $1$ m $= 10$ dm $= 100$ cm $= 1000$ mm.
• Arie: fiecare treaptă înseamnă de $100$ de ori: $1$ m² $= 100$ dm² $= 10\,000$ cm².
• Arii de teren: $1$ ar $= 100$ m²; $1$ ha $= 10\,000$ m².
• Volum: fiecare treaptă înseamnă de $1000$ de ori: $1$ m³ $= 1000$ dm³ $= 1\,000\,000$ cm³.
• Capacitate: $1$ l $= 1$ dm³ $= 1000$ ml; $1$ hl $= 100$ l.
• Masă: $1$ t $= 1000$ kg; $1$ kg $= 1000$ g.
• Timp: $1$ zi $= 24$ h; $1$ h $= 60$ min; $1$ min $= 60$ s.
• Bani: $1$ leu $= 100$ de bani.$EN$
WHERE code = 'G1' AND notes <> '';
INSERT INTO en_chapter_examples (chapter_id, position, statement, solution)
SELECT c.id, last.position + e.position, e.statement, e.solution
FROM en_chapters c
CROSS JOIN LATERAL (SELECT MAX(x.position) AS position FROM en_chapter_examples x WHERE x.chapter_id = c.id) last
CROSS JOIN (VALUES
    (1, $EN$Transformați: $2{,}5$ km în metri, $3$ m² în cm² și $1{,}2$ m³ în litri.$EN$,
        $EN$$2{,}5$ km $= 2{,}5 \cdot 1000$ m $= 2500$ m.
$3$ m² $= 3 \cdot 10\,000$ cm² $= 30\,000$ cm².
$1{,}2$ m³ $= 1200$ dm³ $= 1200$ l.$EN$),
    (2, $EN$Un teren dreptunghiular are dimensiunile de $120$ m și $50$ m. Exprimați aria lui în hectare.$EN$,
        $EN$Aria: $120 \cdot 50 = 6000$ m².
$6000 : 10\,000 = 0{,}6$, deci aria este $0{,}6$ ha.$EN$)
) AS e (position, statement, solution)
WHERE c.code = 'G1' AND last.position IS NOT NULL;

-- G2
UPDATE en_chapters SET notes = notes || E'\n\n' || $EN$Clasificarea unghiurilor
• Unghi nul: $0^\circ$. Ascuțit: mai mic decât $90^\circ$. Drept: $90^\circ$. Obtuz: între $90^\circ$ și $180^\circ$. Alungit: $180^\circ$.

Calcule cu grade și minute
• $1^\circ = 60'$.
• La adunare, dacă minutele trec de $60$, transformi $60'$ în $1^\circ$: $35^\circ 40' + 20^\circ 35' = 55^\circ 75' = 56^\circ 15'$.
• La scădere, dacă nu ajung minutele, transformi $1^\circ$ în $60'$: $90^\circ - 35^\circ 40' = 89^\circ 60' - 35^\circ 40' = 54^\circ 20'$.

Axioma paralelelor și distanța la o dreaptă
• Printr-un punct exterior unei drepte trece o singură paralelă la acea dreaptă.
• Distanța de la un punct la o dreaptă este lungimea perpendicularei duse din punct pe dreaptă. Orice oblică dusă din același punct este mai lungă.$EN$
WHERE code = 'G2' AND notes <> '';
INSERT INTO en_chapter_examples (chapter_id, position, statement, solution)
SELECT c.id, last.position + e.position, e.statement, e.solution
FROM en_chapters c
CROSS JOIN LATERAL (SELECT MAX(x.position) AS position FROM en_chapter_examples x WHERE x.chapter_id = c.id) last
CROSS JOIN (VALUES
    (1, $EN$Calculați complementul unui unghi de $37^\circ 25'$.$EN$,
        $EN$$90^\circ - 37^\circ 25' = 89^\circ 60' - 37^\circ 25' = 52^\circ 35'$.$EN$),
    (2, $EN$Calculați $3 \cdot 24^\circ 30'$.$EN$,
        $EN$$3 \cdot 24^\circ = 72^\circ$ și $3 \cdot 30' = 90' = 1^\circ 30'$.
Rezultatul este $73^\circ 30'$.$EN$)
) AS e (position, statement, solution)
WHERE c.code = 'G2' AND last.position IS NOT NULL;

-- G3
UPDATE en_chapters SET notes = notes || E'\n\n' || $EN$Inegalități în triunghi
• Orice latură este mai mică decât suma celorlalte două.
• Față de unghiul mai mare se află latura mai mare, și reciproc.

Punctele de pe bisectoare și de pe mediatoare
• Orice punct de pe bisectoarea unui unghi este la distanțe egale de laturile unghiului.
• Orice punct de pe mediatoarea unui segment este la distanțe egale de capetele segmentului.

Unde se întâlnesc liniile importante
• Bisectoarele: în centrul cercului înscris în triunghi, la distanțe egale de laturi.
• Mediatoarele: în centrul cercului circumscris triunghiului, la distanțe egale de vârfuri.
• Înălțimile: în ortocentru.
• Medianele: în centrul de greutate.

Simetria față de o dreaptă
• Simetricul punctului $A$ față de dreapta $d$ este punctul $A'$ pentru care $d$ este mediatoarea segmentului $AA'$.$EN$
WHERE code = 'G3' AND notes <> '';
INSERT INTO en_chapter_examples (chapter_id, position, statement, solution)
SELECT c.id, last.position + e.position, e.statement, e.solution
FROM en_chapters c
CROSS JOIN LATERAL (SELECT MAX(x.position) AS position FROM en_chapter_examples x WHERE x.chapter_id = c.id) last
CROSS JOIN (VALUES
    (1, $EN$Pot fi $3$ cm, $4$ cm și $8$ cm lungimile laturilor unui triunghi?$EN$,
        $EN$Cea mai mare latură trebuie să fie mai mică decât suma celorlalte două.
$3 + 4 = 7 < 8$, deci nu există un astfel de triunghi.$EN$),
    (2, $EN$Punctul $D$ aparține mediatoarei segmentului $BC$, cu $DB = 6$ cm și $BC = 4$ cm. Calculați perimetrul triunghiului $DBC$.$EN$,
        $EN$$D$ este pe mediatoare, deci $DC = DB = 6$ cm.
Perimetrul: $6 + 6 + 4 = 16$ cm.$EN$)
) AS e (position, statement, solution)
WHERE c.code = 'G3' AND last.position IS NOT NULL;

-- G4
UPDATE en_chapters SET notes = notes || E'\n\n' || $EN$Cotangenta și valorile de reținut
• $\operatorname{ctg} B = \frac{AB}{AC}$ (cateta alăturată / cateta opusă).

| Unghiul | $30^\circ$ | $45^\circ$ | $60^\circ$ |
|---|---|---|---|
| sin | $\frac{1}{2}$ | $\frac{\sqrt{2}}{2}$ | $\frac{\sqrt{3}}{2}$ |
| cos | $\frac{\sqrt{3}}{2}$ | $\frac{\sqrt{2}}{2}$ | $\frac{1}{2}$ |
| tg | $\frac{\sqrt{3}}{3}$ | $1$ | $\sqrt{3}$ |
| ctg | $\sqrt{3}$ | $1$ | $\frac{\sqrt{3}}{3}$ |

Reciprocele
• Dacă într-un triunghi dreptunghic o catetă este jumătate din ipotenuză, unghiul opus ei are $30^\circ$.
• Dacă mediana dusă dintr-un vârf este jumătate din latura pe care cade, triunghiul este dreptunghic în acel vârf.

Poligoane regulate înscrise în cercul de rază $R$ ($l$ latura, $a$ apotema)
• Triunghiul echilateral: $l = R\sqrt{3}$, $a = \frac{R}{2}$.
• Pătratul: $l = R\sqrt{2}$, $a = \frac{l}{2}$.
• Hexagonul regulat: $l = R$, $a = \frac{l\sqrt{3}}{2}$, aria $= \frac{3l^2\sqrt{3}}{2}$. Hexagonul este format din șase triunghiuri echilaterale.$EN$
WHERE code = 'G4' AND notes <> '';
INSERT INTO en_chapter_examples (chapter_id, position, statement, solution)
SELECT c.id, last.position + e.position, e.statement, e.solution
FROM en_chapters c
CROSS JOIN LATERAL (SELECT MAX(x.position) AS position FROM en_chapter_examples x WHERE x.chapter_id = c.id) last
CROSS JOIN (VALUES
    (1, $EN$În triunghiul $ABC$, dreptunghic în $A$, $AB = 8$ cm și $AC = 6$ cm. Calculați $\sin B$, $\cos B$, $\operatorname{tg} B$ și $\operatorname{ctg} B$.$EN$,
        $EN$$BC = \sqrt{8^2 + 6^2} = 10$ cm.
$\sin B = \frac{6}{10} = \frac{3}{5}$
$\cos B = \frac{8}{10} = \frac{4}{5}$
$\operatorname{tg} B = \frac{6}{8} = \frac{3}{4}$
$\operatorname{ctg} B = \frac{8}{6} = \frac{4}{3}$$EN$),
    (2, $EN$Un hexagon regulat este înscris într-un cerc cu raza de $6$ cm. Calculați latura, apotema și aria hexagonului.$EN$,
        $EN$Latura este egală cu raza: $l = 6$ cm.
Apotema: $a = \frac{6\sqrt{3}}{2} = 3\sqrt{3}$ cm.
Aria: $\frac{3 \cdot 36\sqrt{3}}{2} = 54\sqrt{3}$ cm².$EN$),
    (3, $EN$La $30$ m de baza unui turn, vârful turnului se vede sub un unghi de $30^\circ$ față de sol. Aproximați înălțimea turnului, știind că $\sqrt{3} \approx 1{,}73$.$EN$,
        $EN$Turnul, solul și raza privirii formează un triunghi dreptunghic.
$h = 30 \cdot \operatorname{tg} 30^\circ = 30 \cdot \frac{\sqrt{3}}{3} = 10\sqrt{3} \approx 17{,}3$ m.$EN$)
) AS e (position, statement, solution)
WHERE c.code = 'G4' AND last.position IS NOT NULL;

-- G5
UPDATE en_chapters SET notes = notes || E'\n\n' || $EN$Patrulaterul convex
• Un patrulater este convex dacă, pentru fiecare latură, celelalte două vârfuri sunt de aceeași parte a dreptei care conține latura.
• Suma măsurilor unghiurilor unui patrulater convex este $360^\circ$.$EN$
WHERE code = 'G5' AND notes <> '';
INSERT INTO en_chapter_examples (chapter_id, position, statement, solution)
SELECT c.id, last.position + e.position, e.statement, e.solution
FROM en_chapters c
CROSS JOIN LATERAL (SELECT MAX(x.position) AS position FROM en_chapter_examples x WHERE x.chapter_id = c.id) last
CROSS JOIN (VALUES
    (1, $EN$Unghiurile unui patrulater convex au măsurile proporționale cu $2$, $3$, $4$ și $6$. Aflați măsurile lor.$EN$,
        $EN$Unghiurile sunt $2x$, $3x$, $4x$, $6x$, iar suma lor este $360^\circ$:
$15x = 360^\circ \Rightarrow x = 24^\circ$.
Măsurile: $48^\circ$, $72^\circ$, $96^\circ$, $144^\circ$.$EN$)
) AS e (position, statement, solution)
WHERE c.code = 'G5' AND last.position IS NOT NULL;

-- G6
UPDATE en_chapters SET notes = notes || E'\n\n' || $EN$Alte proprietăți ale coardelor
• Diametrul perpendicular pe o coardă trece prin mijlocul coardei și prin mijloacele arcelor ei.
• Arcele cuprinse între două coarde paralele sunt congruente.
• Două coarde congruente sunt egal depărtate de centru, și reciproc.

Pozițiile unei drepte față de un cerc
Notăm cu $r$ raza și cu $d$ distanța de la centru la dreaptă.
• $d > r$: dreapta este exterioară cercului (niciun punct comun).
• $d = r$: dreapta este tangentă (un punct comun).
• $d < r$: dreapta este secantă (două puncte comune).

Pozițiile a două cercuri
Notăm cu $R$ și $r$ razele, $R > r$, și cu $d$ distanța dintre centre.
• $d > R + r$: cercuri exterioare.
• $d = R + r$: tangente exterior.
• $R - r < d < R + r$: secante.
• $d = R - r$: tangente interior.
• $d < R - r$: cercul mic este în interiorul celui mare.$EN$
WHERE code = 'G6' AND notes <> '';
INSERT INTO en_chapter_examples (chapter_id, position, statement, solution)
SELECT c.id, last.position + e.position, e.statement, e.solution
FROM en_chapters c
CROSS JOIN LATERAL (SELECT MAX(x.position) AS position FROM en_chapter_examples x WHERE x.chapter_id = c.id) last
CROSS JOIN (VALUES
    (1, $EN$Un cerc are raza de $13$ cm, iar coarda $AB$ are $24$ cm. Calculați distanța de la centrul cercului la coardă.$EN$,
        $EN$Perpendiculara din centrul $O$ pe coardă trece prin mijlocul $M$ al coardei, deci $AM = 12$ cm.
Triunghiul $OMA$ este dreptunghic în $M$: $OM^2 = 13^2 - 12^2 = 25$, deci $OM = 5$ cm.$EN$),
    (2, $EN$Două cercuri au razele de $7$ cm și $3$ cm. Ce poziție au dacă distanța dintre centre este: a) $10$ cm; b) $12$ cm; c) $5$ cm; d) $2$ cm?$EN$,
        $EN$$R + r = 10$ și $R - r = 4$.
a) $d = 10 = R + r$: tangente exterior.
b) $d = 12 > 10$: exterioare.
c) $4 < 5 < 10$: secante.
d) $d = 2 < 4$: cercul mic este în interiorul celui mare.$EN$)
) AS e (position, statement, solution)
WHERE c.code = 'G6' AND last.position IS NOT NULL;

-- G7
UPDATE en_chapters SET notes = notes || E'\n\n' || $EN$Teorema paralelelor echidistante
• Dacă mai multe drepte paralele determină pe o secantă segmente congruente, atunci ele determină segmente congruente pe orice altă secantă.

Împărțirea unui segment în părți proporționale
• Pentru $AM : MB = m : n$, notezi $AM = m \cdot k$ și $MB = n \cdot k$, de unde $k = \frac{AB}{m + n}$.
• Construcția se face cu paralele, pe baza teoremei lui Thales.

Aproximarea distanțelor
• Un obiect și umbra lui, în același moment, formează un triunghi asemenea cu cel format de alt obiect și umbra lui. Așa afli înălțimi pe care nu le poți măsura direct.$EN$
WHERE code = 'G7' AND notes <> '';
INSERT INTO en_chapter_examples (chapter_id, position, statement, solution)
SELECT c.id, last.position + e.position, e.statement, e.solution
FROM en_chapters c
CROSS JOIN LATERAL (SELECT MAX(x.position) AS position FROM en_chapter_examples x WHERE x.chapter_id = c.id) last
CROSS JOIN (VALUES
    (1, $EN$Punctul $M$ împarte segmentul $AB = 21$ cm astfel încât $AM : MB = 3 : 4$. Calculați $AM$ și $MB$.$EN$,
        $EN$$k = \frac{21}{3 + 4} = 3$ cm.
$AM = 3 \cdot 3 = 9$ cm și $MB = 4 \cdot 3 = 12$ cm.$EN$),
    (2, $EN$Un băț de $2$ m are umbra de $3$ m. În același moment, umbra unui copac are $12$ m. Ce înălțime are copacul?$EN$,
        $EN$Triunghiurile sunt asemenea, deci $\frac{h}{12} = \frac{2}{3}$.
$h = \frac{2 \cdot 12}{3} = 8$ m.$EN$)
) AS e (position, statement, solution)
WHERE c.code = 'G7' AND last.position IS NOT NULL;

-- G8
UPDATE en_chapters SET notes = notes || E'\n\n' || $EN$Tetraedrul regulat (muchia $l$)
• Are patru fețe, toate triunghiuri echilaterale.
• $A_t = l^2\sqrt{3}$, înălțimea $h = \frac{l\sqrt{6}}{3}$, $V = \frac{l^3\sqrt{2}}{12}$.

Baza hexagon regulat (latura $l$)
• $P_b = 6l$, $A_b = \frac{3l^2\sqrt{3}}{2}$, apotema bazei $\frac{l\sqrt{3}}{2}$.
• Prisma și piramida cu această bază folosesc aceleași formule ca mai sus.$EN$
WHERE code = 'G8' AND notes <> '';
INSERT INTO en_chapter_examples (chapter_id, position, statement, solution)
SELECT c.id, last.position + e.position, e.statement, e.solution
FROM en_chapters c
CROSS JOIN LATERAL (SELECT MAX(x.position) AS position FROM en_chapter_examples x WHERE x.chapter_id = c.id) last
CROSS JOIN (VALUES
    (1, $EN$Un tetraedru regulat are muchia de $6$ cm. Calculați aria totală, înălțimea și volumul.$EN$,
        $EN$$A_t = 6^2\sqrt{3} = 36\sqrt{3}$ cm².
$h = \frac{6\sqrt{6}}{3} = 2\sqrt{6}$ cm.
$V = \frac{6^3\sqrt{2}}{12} = \frac{216\sqrt{2}}{12} = 18\sqrt{2}$ cm³.$EN$),
    (2, $EN$O prismă dreaptă are baza hexagon regulat cu latura de $2$ cm și înălțimea de $5$ cm. Calculați aria laterală și volumul.$EN$,
        $EN$$P_b = 6 \cdot 2 = 12$ cm, deci $A_l = 12 \cdot 5 = 60$ cm².
$A_b = \frac{3 \cdot 2^2\sqrt{3}}{2} = 6\sqrt{3}$ cm², deci $V = 6\sqrt{3} \cdot 5 = 30\sqrt{3}$ cm³.$EN$)
) AS e (position, statement, solution)
WHERE c.code = 'G8' AND last.position IS NOT NULL;

-- G9
UPDATE en_chapters SET notes = notes || E'\n\n' || $EN$Determinarea planului
• Un plan este determinat de: trei puncte necoliniare; o dreaptă și un punct din afara ei; două drepte concurente; două drepte paralele.

Paralelism
• O dreaptă din afara unui plan, paralelă cu o dreaptă din plan, este paralelă cu planul.
• Două plane sunt paralele dacă unul conține două drepte concurente, ambele paralele cu celălalt plan.
• Distanța dintre două plane paralele este distanța de la un punct al unui plan la celălalt plan. La prismă și la trunchi, ea este chiar înălțimea.

Plane perpendiculare
• Două plane sunt perpendiculare dacă unghiul dintre ele are $90^\circ$. Se întâmplă când unul conține o dreaptă perpendiculară pe celălalt.

Proiecții pe un plan
• Proiecția unui punct $A$ pe planul $\alpha$ este piciorul perpendicularei din $A$ pe $\alpha$.
• Lungimea proiecției unui segment: $A'B' = AB \cdot \cos \varphi$, unde $\varphi$ este unghiul dintre dreapta $AB$ și plan.$EN$
WHERE code = 'G9' AND notes <> '';
INSERT INTO en_chapter_examples (chapter_id, position, statement, solution)
SELECT c.id, last.position + e.position, e.statement, e.solution
FROM en_chapters c
CROSS JOIN LATERAL (SELECT MAX(x.position) AS position FROM en_chapter_examples x WHERE x.chapter_id = c.id) last
CROSS JOIN (VALUES
    (1, $EN$Un segment de $10$ cm formează cu un plan un unghi de $60^\circ$. Calculați lungimea proiecției lui pe plan.$EN$,
        $EN$Lungimea proiecției este $10 \cdot \cos 60^\circ = 10 \cdot \frac{1}{2} = 5$ cm.$EN$),
    (2, $EN$Cubul $ABCDA'B'C'D'$ are muchia de $4$ cm. Calculați distanța dintre planele $(ABCD)$ și $(A'B'C'D')$ și arătați că $(ABB') \perp (ABCD)$.$EN$,
        $EN$Planele sunt paralele, iar $AA'$ este perpendiculară pe ambele, deci distanța este $AA' = 4$ cm.
$AA' \perp (ABCD)$, iar $AA'$ este inclusă în planul $(ABB')$, deci $(ABB') \perp (ABCD)$.$EN$)
) AS e (position, statement, solution)
WHERE c.code = 'G9' AND last.position IS NOT NULL;
