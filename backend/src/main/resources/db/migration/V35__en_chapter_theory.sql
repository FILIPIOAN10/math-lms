-- Theory for the 20 EN chapter pages (V33): the text at the top of each page and a few worked examples, written
-- after docs/EN-capitole.md (official EN papers 2023-2026). A starting point the teacher edits freely: a page that
-- already has text keeps it, and a page that already has worked examples gets none of these. Plain text with LaTeX
-- between $...$, shown as written (line breaks kept).

-- A1
UPDATE en_chapters SET notes = $EN$Ordinea efectuării operațiilor

1. Parantezele: întâi cele rotunde ( ), apoi cele pătrate [ ], la urmă acoladele { }.
2. Puterile.
3. Înmulțirile și împărțirile, în ordinea în care apar, de la stânga la dreapta.
4. Adunările și scăderile, tot de la stânga la dreapta.

Reguli de calcul cu puteri
• $a^m \cdot a^n = a^{m+n}$
• $a^m : a^n = a^{m-n}$
• $(a^m)^n = a^{m \cdot n}$
• $(a \cdot b)^n = a^n \cdot b^n$
• $a^1 = a$; $a^0 = 1$ pentru $a \neq 0$; $1^n = 1$

Atenție
• Înmulțirea și împărțirea au aceeași prioritate: $24 : 4 \cdot 2 = 6 \cdot 2 = 12$, nu $24 : 8 = 3$.
• Puterea se calculează înaintea înmulțirii: $2 \cdot 3^2 = 2 \cdot 9 = 18$, nu $6^2 = 36$.
• La test, printre variante apare aproape mereu rezultatul obținut calculând de la stânga la dreapta, fără prioritate. Verifică ordinea înainte să alegi.$EN$
WHERE code = 'A1' AND notes IS NULL;
INSERT INTO en_chapter_examples (chapter_id, position, statement, solution)
SELECT c.id, e.position, e.statement, e.solution
FROM en_chapters c
CROSS JOIN (VALUES
    (1, $EN$Calculează $30 - 18 : 3$.$EN$,
        $EN$Împărțirea are prioritate față de scădere:
$18 : 3 = 6$
$30 - 6 = 24$
Rezultat: $24$. (Greșit: $(30 - 18) : 3 = 4$.)$EN$),
    (2, $EN$Calculează $50 - (12 + 8) : 4$.$EN$,
        $EN$Întâi paranteza: $12 + 8 = 20$.
Apoi împărțirea: $20 : 4 = 5$.
La final scăderea: $50 - 5 = 45$.$EN$),
    (3, $EN$Calculează $2^3 + 3 \cdot 2^2$.$EN$,
        $EN$Întâi puterile: $2^3 = 8$ și $2^2 = 4$.
Apoi înmulțirea: $3 \cdot 4 = 12$.
La final adunarea: $8 + 12 = 20$.$EN$),
    (4, $EN$Calculează $[(15 - 3 \cdot 4)^2 + 1] \cdot 2$.$EN$,
        $EN$Lucrăm din interior spre exterior.
Paranteza rotundă: $3 \cdot 4 = 12$, apoi $15 - 12 = 3$.
Puterea: $3^2 = 9$.
Paranteza pătrată: $9 + 1 = 10$.
La final: $10 \cdot 2 = 20$.$EN$),
    (5, $EN$Calculează $2^5 \cdot 2^3 : 2^6$.$EN$,
        $EN$Folosim regulile puterilor cu aceeași bază:
$2^5 \cdot 2^3 = 2^{5+3} = 2^8$
$2^8 : 2^6 = 2^{8-6} = 2^2 = 4$.$EN$)
) AS e (position, statement, solution)
WHERE c.code = 'A1'
  AND NOT EXISTS (SELECT 1 FROM en_chapter_examples x WHERE x.chapter_id = c.id);

-- A2
UPDATE en_chapters SET notes = $EN$Rapoarte și proporții

• Raportul numerelor $a$ și $b$ ($b \neq 0$) este $\frac{a}{b}$.
• O proporție este o egalitate de două rapoarte: $\frac{a}{b} = \frac{c}{d}$.
• Proprietatea fundamentală: produsul extremilor este egal cu produsul mezilor:
$\frac{a}{b} = \frac{c}{d} \Leftrightarrow a \cdot d = b \cdot c$.
• Aflarea unui termen necunoscut: $\frac{x}{b} = \frac{c}{d} \Rightarrow x = \frac{b \cdot c}{d}$.

Șir de rapoarte egale
Dacă $\frac{a}{2} = \frac{b}{3} = \frac{c}{5} = k$, atunci $a = 2k$, $b = 3k$, $c = 5k$, iar
$\frac{a + b + c}{2 + 3 + 5} = k$.

Mărimi proporționale
• Direct proporționale („cu cât una crește, cu atât crește și cealaltă”): $a$ și $b$ sunt direct proporționale cu $2$ și $3$ dacă $\frac{a}{2} = \frac{b}{3}$.
• Invers proporționale: $a$ și $b$ sunt invers proporționale cu $2$ și $3$ dacă $2a = 3b$, adică $\frac{a}{3} = \frac{b}{2}$.
• Exemplu practic: dacă numărul de muncitori crește, numărul de zile scade — produsul lor rămâne constant.

Atenție
• Dintr-un raport cunoscut poți scoate o relație: $\frac{a}{b} = \frac{3}{2} \Rightarrow 2a = 3b$. Asta ajută la expresii ca $2a - 3b$.$EN$
WHERE code = 'A2' AND notes IS NULL;
INSERT INTO en_chapter_examples (chapter_id, position, statement, solution)
SELECT c.id, e.position, e.statement, e.solution
FROM en_chapters c
CROSS JOIN (VALUES
    (1, $EN$Aflați $x$ din proporția $\frac{x}{3} = \frac{4}{6}$.$EN$,
        $EN$Produsul extremilor = produsul mezilor:
$6x = 3 \cdot 4 = 12$, deci $x = 2$.$EN$),
    (2, $EN$Dacă $\frac{a}{b} = \frac{3}{2}$, calculați $2a - 3b + 5$.$EN$,
        $EN$Din proporție: $2a = 3b$, deci $2a - 3b = 0$.
Atunci $2a - 3b + 5 = 0 + 5 = 5$.$EN$),
    (3, $EN$Numerele $a$ și $b$ sunt direct proporționale cu $2$ și $3$, iar $a + b = 20$. Aflați $a$ și $b$.$EN$,
        $EN$$\frac{a}{2} = \frac{b}{3} = k$, deci $a = 2k$ și $b = 3k$.
$2k + 3k = 20 \Rightarrow 5k = 20 \Rightarrow k = 4$.
Deci $a = 8$ și $b = 12$.$EN$),
    (4, $EN$6 muncitori termină o lucrare în 10 zile. În câte zile o termină 4 muncitori, lucrând la fel de repede?$EN$,
        $EN$Numărul muncitorilor și numărul zilelor sunt invers proporționale: produsul rămâne același.
$6 \cdot 10 = 60$, deci $4 \cdot z = 60 \Rightarrow z = 15$ zile.$EN$)
) AS e (position, statement, solution)
WHERE c.code = 'A2'
  AND NOT EXISTS (SELECT 1 FROM en_chapter_examples x WHERE x.chapter_id = c.id);

-- A3
UPDATE en_chapters SET notes = $EN$Procente și fracții dintr-un număr

• $p\%$ înseamnă $\frac{p}{100}$.
• $p\%$ din $N$ $= \frac{p}{100} \cdot N$.
• O fracție dintr-un număr: $\frac{a}{b}$ din $N$ $= \frac{a}{b} \cdot N$.

Scumpiri și reduceri
• După o scumpire cu $p\%$, prețul devine $\left(1 + \frac{p}{100}\right) \cdot$ preț. Exemplu: $+10\%$ înseamnă $\cdot 1{,}1$.
• După o reducere cu $p\%$, prețul devine $\left(1 - \frac{p}{100}\right) \cdot$ preț. Exemplu: $-20\%$ înseamnă $\cdot 0{,}8$.

Aflarea numărului și a procentului
• Dacă $p\%$ dintr-un număr este $a$, numărul este $\frac{a \cdot 100}{p}$.
• Ce procent reprezintă $a$ din $N$: $\frac{a}{N} \cdot 100\%$.

Atenție
• O scumpire cu $10\%$ urmată de o reducere cu $10\%$ NU readuce prețul inițial: $100 \to 110 \to 99$.
• Procentul se aplică mereu la valoarea „de dinainte” (prețul inițial, totalul).$EN$
WHERE code = 'A3' AND notes IS NULL;
INSERT INTO en_chapter_examples (chapter_id, position, statement, solution)
SELECT c.id, e.position, e.statement, e.solution
FROM en_chapters c
CROSS JOIN (VALUES
    (1, $EN$Calculează $25\%$ din $80$.$EN$,
        $EN$$25\%$ din $80 = \frac{25}{100} \cdot 80 = \frac{1}{4} \cdot 80 = 20$.$EN$),
    (2, $EN$Un produs costă $150$ de lei. Cât costă după o scumpire cu $10\%$?$EN$,
        $EN$Scumpirea: $10\%$ din $150 = 15$ lei.
Prețul nou: $150 + 15 = 165$ de lei.
(Sau direct: $150 \cdot 1{,}1 = 165$.)$EN$),
    (3, $EN$O bicicletă costă $800$ de lei. Se ieftinește cu $15\%$. Cât costă acum?$EN$,
        $EN$Reducerea: $\frac{15}{100} \cdot 800 = 120$ de lei.
Prețul nou: $800 - 120 = 680$ de lei.$EN$),
    (4, $EN$$30\%$ dintr-un număr este $12$. Care este numărul?$EN$,
        $EN$$\frac{30}{100} \cdot N = 12 \Rightarrow N = \frac{12 \cdot 100}{30} = 40$.$EN$),
    (5, $EN$Într-o clasă sunt $24$ de elevi, dintre care $18$ au promovat simularea. Ce procent au promovat?$EN$,
        $EN$$\frac{18}{24} \cdot 100\% = \frac{3}{4} \cdot 100\% = 75\%$.$EN$)
) AS e (position, statement, solution)
WHERE c.code = 'A3'
  AND NOT EXISTS (SELECT 1 FROM en_chapter_examples x WHERE x.chapter_id = c.id);

-- A4
UPDATE en_chapters SET notes = $EN$Numere întregi

• Opusul lui $a$ este $-a$: opusul lui $5$ este $-5$, opusul lui $-3$ este $3$.
• Modulul (valoarea absolută) este distanța până la $0$: $|7| = 7$, $|-7| = 7$, $|0| = 0$.
• Pe axă, numărul din dreapta e mai mare: $-5 < -2 < 0 < 3$. Dintre două numere negative, mai mare e cel cu modulul mai mic.

Adunare și scădere
• Semne la fel: aduni modulele și păstrezi semnul: $-3 + (-4) = -7$.
• Semne diferite: scazi modulele și iei semnul celui cu modulul mai mare: $-9 + 4 = -5$.
• A scădea un număr înseamnă a aduna opusul lui: $-5 - (-8) = -5 + 8 = 3$.

Înmulțire și împărțire — regula semnelor
• $(+) \cdot (+) = +$, $(-) \cdot (-) = +$
• $(+) \cdot (-) = -$, $(-) \cdot (+) = -$
(La fel la împărțire.)

Puteri cu bază negativă
• Exponent par → rezultat pozitiv: $(-2)^2 = 4$, $(-1)^{2024} = 1$.
• Exponent impar → rezultat negativ: $(-2)^3 = -8$, $(-1)^{2025} = -1$.

Atenție
• $-3^2 = -9$ (puterea se aplică doar lui $3$), dar $(-3)^2 = 9$.$EN$
WHERE code = 'A4' AND notes IS NULL;
INSERT INTO en_chapter_examples (chapter_id, position, statement, solution)
SELECT c.id, e.position, e.statement, e.solution
FROM en_chapters c
CROSS JOIN (VALUES
    (1, $EN$Calculează $(-4) \cdot 3 - (-6) : 2$.$EN$,
        $EN$$(-4) \cdot 3 = -12$
$(-6) : 2 = -3$
$-12 - (-3) = -12 + 3 = -9$.$EN$),
    (2, $EN$Calculează $-5 - (-8)$.$EN$,
        $EN$Scăderea unui număr negativ devine adunare: $-5 + 8 = 3$.$EN$),
    (3, $EN$Calculează $(-2)^3 + (-2)^2$.$EN$,
        $EN$$(-2)^3 = -8$ (exponent impar), $(-2)^2 = 4$ (exponent par).
$-8 + 4 = -4$.$EN$),
    (4, $EN$Calculează produsul dintre $3$ și opusul lui $3$.$EN$,
        $EN$Opusul lui $3$ este $-3$.
$3 \cdot (-3) = -9$.$EN$),
    (5, $EN$Ordonează crescător: $-2$, $5$, $-7$, $0$, $|-4|$.$EN$,
        $EN$$|-4| = 4$.
Crescător: $-7 < -2 < 0 < 4 < 5$, adică $-7$, $-2$, $0$, $|-4|$, $5$.$EN$)
) AS e (position, statement, solution)
WHERE c.code = 'A4'
  AND NOT EXISTS (SELECT 1 FROM en_chapter_examples x WHERE x.chapter_id = c.id);

-- A5
UPDATE en_chapters SET notes = $EN$Mulțimi, intervale, divizibilitate

Mulțimi
• $x \in A$: $x$ aparține lui $A$; $A \subset B$: orice element al lui $A$ e și în $B$.
• Reuniunea $A \cup B$: elementele din $A$ sau din $B$ (scrise o singură dată).
• Intersecția $A \cap B$: elementele comune.
• Diferența $A \setminus B$: elementele din $A$ care nu sunt în $B$.

Intervale de numere reale
• $[a, b]$: $a \le x \le b$ (capete incluse, paranteză pătrată, buline pline pe axă).
• $(a, b)$: $a < x < b$ (capete excluse, buline goale).
• $[a, b)$, $(a, b]$: un capăt inclus, celălalt nu.
• $(-\infty, a)$, $[a, +\infty)$: la infinit paranteza e mereu rotundă.
• Intersecția și reuniunea le citești pe axă: desenează ambele intervale una sub alta.

Inecuații de forma $ax + b < 0$
• Treci termenul liber în partea cealaltă și împarți la $a$.
• Dacă împarți la un număr NEGATIV, semnul inegalității se schimbă: $-2x < 6 \Rightarrow x > -3$.

Divizibilitate
• Criterii: cu $2$ (ultima cifră pară), cu $5$ (ultima cifră $0$ sau $5$), cu $10$ (ultima cifră $0$), cu $3$ / cu $9$ (suma cifrelor se divide cu $3$ / cu $9$).
• Număr prim: are exact doi divizori, $1$ și el însuși ($2, 3, 5, 7, 11, 13, \ldots$). $1$ nu este prim.
• c.m.m.d.c.: produsul factorilor primi comuni, la puterea cea mai mică.
• c.m.m.m.c.: produsul tuturor factorilor primi, la puterea cea mai mare.$EN$
WHERE code = 'A5' AND notes IS NULL;
INSERT INTO en_chapter_examples (chapter_id, position, statement, solution)
SELECT c.id, e.position, e.statement, e.solution
FROM en_chapters c
CROSS JOIN (VALUES
    (1, $EN$Calculează $[-1, 4) \cap (2, 7]$ și $[-1, 4) \cup (2, 7]$.$EN$,
        $EN$Pe axă: primul interval merge de la $-1$ (inclus) la $4$ (exclus), al doilea de la $2$ (exclus) la $7$ (inclus).
Partea comună: între $2$ și $4$, ambele capete excluse: $[-1, 4) \cap (2, 7] = (2, 4)$.
Reuniunea acoperă totul de la $-1$ la $7$: $[-1, 4) \cup (2, 7] = [-1, 7]$.$EN$),
    (2, $EN$Care este cel mai mare număr întreg din intervalul $(-3, 6)$? Câte numere întregi conține intervalul $[-2, 3)$?$EN$,
        $EN$$6$ nu aparține intervalului (paranteză rotundă), deci cel mai mare întreg este $5$.
În $[-2, 3)$ sunt $-2, -1, 0, 1, 2$, adică $5$ numere întregi ($3$ nu e inclus).$EN$),
    (3, $EN$Rezolvă inecuația $2x - 4 < 0$ și scrie soluția ca interval.$EN$,
        $EN$$2x < 4 \Rightarrow x < 2$.
Soluția: $x \in (-\infty, 2)$.$EN$),
    (4, $EN$Aflați c.m.m.d.c. și c.m.m.m.c. al numerelor $12$ și $18$.$EN$,
        $EN$$12 = 2^2 \cdot 3$, $18 = 2 \cdot 3^2$.
c.m.m.d.c. $= 2 \cdot 3 = 6$ (factorii comuni, puterea mică).
c.m.m.m.c. $= 2^2 \cdot 3^2 = 36$ (toți factorii, puterea mare).$EN$),
    (5, $EN$Fie $A = \{1, 2, 3, 4\}$ și $B = \{3, 4, 5\}$. Aflați $A \cup B$, $A \cap B$ și $A \setminus B$.$EN$,
        $EN$$A \cup B = \{1, 2, 3, 4, 5\}$
$A \cap B = \{3, 4\}$
$A \setminus B = \{1, 2\}$$EN$)
) AS e (position, statement, solution)
WHERE c.code = 'A5'
  AND NOT EXISTS (SELECT 1 FROM en_chapter_examples x WHERE x.chapter_id = c.id);

-- A6
UPDATE en_chapters SET notes = $EN$Numere raționale și reale: radicali, modul, comparare

Fracții zecimale periodice
• $0{,}(3) = \frac{3}{9} = \frac{1}{3}$; $0{,}(12) = \frac{12}{99}$.
• Periodică mixtă: $1{,}2(3) = \frac{123 - 12}{90} = \frac{111}{90} = \frac{37}{30}$.
• La comparare, scrie mai multe zecimale: $2{,}(3) = 2{,}3333\ldots$, $2{,}3(2) = 2{,}3222\ldots$

Radicali
• $\sqrt{a^2} = |a|$; de exemplu $\sqrt{(1 - \sqrt{2})^2} = |1 - \sqrt{2}| = \sqrt{2} - 1$.
• Scoaterea factorilor: $\sqrt{12} = \sqrt{4 \cdot 3} = 2\sqrt{3}$; introducerea: $3\sqrt{2} = \sqrt{18}$.
• $\sqrt{a} \cdot \sqrt{b} = \sqrt{ab}$; $\frac{\sqrt{a}}{\sqrt{b}} = \sqrt{\frac{a}{b}}$.
• Aduni doar radicali „asemenea”: $2\sqrt{3} + 3\sqrt{3} = 5\sqrt{3}$, dar $\sqrt{2} + \sqrt{3} \neq \sqrt{5}$.
• Raționalizare: $\frac{6}{\sqrt{3}} = \frac{6\sqrt{3}}{3} = 2\sqrt{3}$.

Modulul
• $|a| = a$ dacă $a \ge 0$ și $|a| = -a$ dacă $a < 0$.
• $|a| \ge 0$ mereu, deci $|a| + |b| = 0 \Rightarrow a = 0$ și $b = 0$.

Media aritmetică
• $m_a = \frac{a + b}{2}$; pentru $n$ numere: suma lor împărțită la $n$.

Compararea numerelor reale
• Introduci factorii sub radical și compari ce e sub radical: $2\sqrt{3} = \sqrt{12} < \sqrt{18} = 3\sqrt{2}$.

Formatul de la EN (S I.5): patru elevi au calculat ceva; alegi elevul care a răspuns corect. Calculează tu, apoi caută rezultatul în tabel.$EN$
WHERE code = 'A6' AND notes IS NULL;
INSERT INTO en_chapter_examples (chapter_id, position, statement, solution)
SELECT c.id, e.position, e.statement, e.solution
FROM en_chapters c
CROSS JOIN (VALUES
    (1, $EN$Calculează $\sqrt{12} + \sqrt{27}$.$EN$,
        $EN$$\sqrt{12} = \sqrt{4 \cdot 3} = 2\sqrt{3}$
$\sqrt{27} = \sqrt{9 \cdot 3} = 3\sqrt{3}$
Suma: $2\sqrt{3} + 3\sqrt{3} = 5\sqrt{3}$.$EN$),
    (2, $EN$Calculează media aritmetică a numerelor $\sqrt{18}$ și $6 - 3\sqrt{2}$.$EN$,
        $EN$$\sqrt{18} = 3\sqrt{2}$.
$m_a = \frac{3\sqrt{2} + 6 - 3\sqrt{2}}{2} = \frac{6}{2} = 3$.$EN$),
    (3, $EN$Dacă $|x - 2| + |y + 5| = 0$, calculați $x + y$.$EN$,
        $EN$Un modul nu e niciodată negativ, deci suma e $0$ doar dacă ambele module sunt $0$:
$x - 2 = 0 \Rightarrow x = 2$ și $y + 5 = 0 \Rightarrow y = -5$.
$x + y = -3$.$EN$),
    (4, $EN$Care este cel mai mare dintre numerele $2{,}(3)$; $2{,}3(2)$; $2{,}33$?$EN$,
        $EN$Scriem mai multe zecimale:
$2{,}(3) = 2{,}3333\ldots$
$2{,}3(2) = 2{,}3222\ldots$
$2{,}33 = 2{,}3300$
Cel mai mare este $2{,}(3)$.$EN$),
    (5, $EN$Raționalizează și calculează: $\frac{6}{\sqrt{3}} - \sqrt{3}$.$EN$,
        $EN$$\frac{6}{\sqrt{3}} = \frac{6\sqrt{3}}{3} = 2\sqrt{3}$.
$2\sqrt{3} - \sqrt{3} = \sqrt{3}$.$EN$)
) AS e (position, statement, solution)
WHERE c.code = 'A6'
  AND NOT EXISTS (SELECT 1 FROM en_chapter_examples x WHERE x.chapter_id = c.id);

-- A7
UPDATE en_chapters SET notes = $EN$Ecuații de gradul I

• O ecuație de forma $ax + b = c$ (cu $a \neq 0$) are o singură soluție.
• Pașii: desfaci parantezele, aduci termenii cu $x$ într-o parte și numerele în cealaltă (schimbând semnul când treci un termen dincolo de egal), reduci termenii asemenea, împarți la coeficientul lui $x$.

Ecuații cu fracții
• Înmulțești toată ecuația cu numitorul comun, ca să scapi de fracții.
• Exemplu: $\frac{x}{2} + \frac{x}{3} = 5$ → înmulțim cu $6$: $3x + 2x = 30$.

Verificarea unei soluții
• „Numărul $k$ este soluția ecuației…” — înlocuiești $x$ cu $k$ în ambele părți. Dacă obții aceeași valoare, afirmația e adevărată.
• La S I.6 (adevărat/fals) e mai rapid să verifici decât să rezolvi.

Atenție
• Un minus în fața parantezei schimbă semnul fiecărui termen: $-(x - 3) = -x + 3$.
• Înmulțești cu numitorul comun TOȚI termenii, inclusiv pe cei fără fracție.$EN$
WHERE code = 'A7' AND notes IS NULL;
INSERT INTO en_chapter_examples (chapter_id, position, statement, solution)
SELECT c.id, e.position, e.statement, e.solution
FROM en_chapters c
CROSS JOIN (VALUES
    (1, $EN$Rezolvă ecuația $3x - 5 = 7$.$EN$,
        $EN$$3x = 7 + 5 = 12$
$x = 12 : 3 = 4$.$EN$),
    (2, $EN$Rezolvă ecuația $2(x - 1) = x + 3$.$EN$,
        $EN$Desfacem paranteza: $2x - 2 = x + 3$.
$2x - x = 3 + 2$
$x = 5$.$EN$),
    (3, $EN$Rezolvă ecuația $\frac{x}{3} + 1 = 3$.$EN$,
        $EN$$\frac{x}{3} = 2 \Rightarrow x = 6$.$EN$),
    (4, $EN$Rezolvă ecuația $\frac{x + 1}{2} = \frac{x - 1}{3}$.$EN$,
        $EN$Produsul extremilor = produsul mezilor: $3(x + 1) = 2(x - 1)$.
$3x + 3 = 2x - 2$
$x = -5$.$EN$),
    (5, $EN$Adevărat sau fals: „Numărul $2$ este soluția ecuației $3x + 1 = x + 5$.”$EN$,
        $EN$Înlocuim $x = 2$:
stânga: $3 \cdot 2 + 1 = 7$; dreapta: $2 + 5 = 7$.
Cele două părți sunt egale, deci afirmația este ADEVĂRATĂ.$EN$)
) AS e (position, statement, solution)
WHERE c.code = 'A7'
  AND NOT EXISTS (SELECT 1 FROM en_chapter_examples x WHERE x.chapter_id = c.id);

-- A8
UPDATE en_chapters SET notes = $EN$Organizarea datelor: diagrame și tabele

• Citește întâi titlul, ce arată fiecare axă și scala (din cât în cât crește gradația).
• Diagrama cu bare: înălțimea fiecărei bare este valoarea categoriei de sub ea.
• Totalul = suma tuturor valorilor (a tuturor barelor sau a coloanei „număr” din tabel).
• Media aritmetică a unor date din tabel: înmulțești fiecare valoare cu de câte ori apare, aduni, apoi împarți la numărul total.
• Procent din total: $\frac{\text{parte}}{\text{total}} \cdot 100\%$.
• Cea mai mare / cea mai mică valoare: bara cea mai înaltă / cea mai joasă — atenție, întrebarea poate cere luna (categoria), nu valoarea.

La EN (S I.6) apare de obicei o afirmație de tip adevărat/fals despre diagramă. Calculează exact ce spune afirmația, nu ce „pare” din desen.

Atenție
• Gradația nu pornește mereu de la $0$ și nu crește mereu din $1$ în $1$.
• „Cel puțin 8” înseamnă $8$ sau mai mult; „mai puțin de 8” înseamnă $7$ sau mai puțin.$EN$
WHERE code = 'A8' AND notes IS NULL;
INSERT INTO en_chapter_examples (chapter_id, position, statement, solution)
SELECT c.id, e.position, e.statement, e.solution
FROM en_chapters c
CROSS JOIN (VALUES
    (1, $EN$Notele unei clase la un test:
nota 5 — 2 elevi
nota 6 — 3 elevi
nota 7 — 5 elevi
nota 8 — 6 elevi
nota 9 — 3 elevi
nota 10 — 1 elev
Câți elevi au dat testul?$EN$,
        $EN$Adunăm numărul elevilor de la fiecare notă:
$2 + 3 + 5 + 6 + 3 + 1 = 20$ de elevi.$EN$),
    (2, $EN$Cu tabelul de la modelul anterior, calculați media notelor clasei.$EN$,
        $EN$Suma notelor: $5 \cdot 2 + 6 \cdot 3 + 7 \cdot 5 + 8 \cdot 6 + 9 \cdot 3 + 10 \cdot 1$
$= 10 + 18 + 35 + 48 + 27 + 10 = 148$.
Media: $148 : 20 = 7{,}4$.$EN$),
    (3, $EN$Cu același tabel: ce procent din elevi au luat cel puțin nota 9?$EN$,
        $EN$„Cel puțin 9” înseamnă nota 9 sau 10: $3 + 1 = 4$ elevi.
$\frac{4}{20} \cdot 100\% = 20\%$.$EN$),
    (4, $EN$O diagramă arată mașinile vândute: ianuarie 12, februarie 15, martie 9, aprilie 14.
Adevărat sau fals: „În martie s-au vândut cu 6 mașini mai puține decât în februarie.”$EN$,
        $EN$Februarie: $15$, martie: $9$. Diferența: $15 - 9 = 6$.
Afirmația este ADEVĂRATĂ.
(Totalul pe cele patru luni: $12 + 15 + 9 + 14 = 50$; cele mai multe s-au vândut în februarie.)$EN$)
) AS e (position, statement, solution)
WHERE c.code = 'A8'
  AND NOT EXISTS (SELECT 1 FROM en_chapter_examples x WHERE x.chapter_id = c.id);

-- A9
UPDATE en_chapters SET notes = $EN$Probleme rezolvate cu ecuații (Subiectul III, problema 1)

Pașii
1. Alegi necunoscuta: scrii clar „Fie $x$ = …”.
2. Exprimi celelalte mărimi cu ajutorul lui $x$.
3. Scrii ecuația din relația dată în enunț.
4. Rezolvi ecuația și verifici rezultatul în enunț.
5. Scrii răspunsul cu unitatea de măsură.

Tipuri care apar
• Vârste: peste $n$ ani fiecare are cu $n$ ani mai mult; acum $n$ ani, cu $n$ mai puțin. Diferența de vârstă rămâne aceeași.
• Bănci / grupe: „câte 2 în bancă, rămân 4 în picioare” → elevi $= 2b + 4$; „câte 3, rămân 2 bănci libere” → elevi $= 3(b - 2)$.
• Prețuri cu procente: un preț este $p\%$ din altul → $\frac{p}{100} \cdot$ celălalt.
• Cu rest: „grupați câte 4 rămân 3” înseamnă $n = 4k + 3$; cauți numerele din interval care îndeplinesc toate condițiile.
• Sisteme: două necunoscute și două relații → substituție sau reducere.

Punctul a) „Verifică dacă… / Este posibil ca…? Justifică”
• Presupui valoarea dată, calculezi și arăți că se potrivește sau că se ajunge la o contradicție. Scrie concluzia într-o propoziție.$EN$
WHERE code = 'A9' AND notes IS NULL;
INSERT INTO en_chapter_examples (chapter_id, position, statement, solution)
SELECT c.id, e.position, e.statement, e.solution
FROM en_chapters c
CROSS JOIN (VALUES
    (1, $EN$Ana are 12 ani, iar mama ei 38 de ani.
a) Este posibil ca peste 2 ani suma vârstelor lor să fie 56 de ani? Justifică.
b) Peste câți ani suma vârstelor lor va fi 60 de ani?$EN$,
        $EN$a) Peste 2 ani: Ana $14$, mama $40$; suma $54 \neq 56$. Nu este posibil.
b) Fie $x$ numărul de ani. Fiecare crește cu $x$:
$(12 + x) + (38 + x) = 60 \Rightarrow 50 + 2x = 60 \Rightarrow x = 5$.
Peste 5 ani (Ana 17, mama 43; $17 + 43 = 60$).$EN$),
    (2, $EN$Dacă elevii unei clase stau câte 2 în bancă, rămân 4 elevi în picioare. Dacă stau câte 3 în bancă, rămân 2 bănci libere. Câte bănci și câți elevi sunt?$EN$,
        $EN$Fie $b$ numărul băncilor.
Câte 2: elevi $= 2b + 4$. Câte 3, cu 2 bănci goale: elevi $= 3(b - 2)$.
$2b + 4 = 3b - 6 \Rightarrow b = 10$ bănci.
Elevi: $2 \cdot 10 + 4 = 24$ (verificare: $3 \cdot 8 = 24$).$EN$),
    (3, $EN$Un pix costă $80\%$ din prețul unui caiet. 2 caiete și 5 pixuri costă împreună 36 de lei. Cât costă un caiet și cât un pix?$EN$,
        $EN$Fie $c$ prețul caietului; pixul costă $0{,}8c$.
$2c + 5 \cdot 0{,}8c = 36 \Rightarrow 2c + 4c = 36 \Rightarrow 6c = 36 \Rightarrow c = 6$ lei.
Pixul: $0{,}8 \cdot 6 = 4{,}8$ lei.$EN$),
    (4, $EN$Numărul elevilor unei școli de vară este între 20 și 30. Grupați câte 4, rămân 3; grupați câte 5, rămân 2. Câți elevi sunt?$EN$,
        $EN$Câte 4 cu rest 3, între 20 și 30: $23$, $27$.
Câte 5 cu rest 2, între 20 și 30: $22$, $27$.
Singurul număr comun: $27$ de elevi.$EN$),
    (5, $EN$Suma a două numere este 50, iar diferența lor este 14. Aflați numerele.$EN$,
        $EN$$a + b = 50$ și $a - b = 14$.
Adunăm relațiile: $2a = 64 \Rightarrow a = 32$.
$b = 50 - 32 = 18$.$EN$)
) AS e (position, statement, solution)
WHERE c.code = 'A9'
  AND NOT EXISTS (SELECT 1 FROM en_chapter_examples x WHERE x.chapter_id = c.id);

-- A10
UPDATE en_chapters SET notes = $EN$Calcul algebric: expresia E(x) (Subiectul III, problema 2)

Formule de calcul prescurtat
• $(a + b)^2 = a^2 + 2ab + b^2$
• $(a - b)^2 = a^2 - 2ab + b^2$
• $(a - b)(a + b) = a^2 - b^2$

Descompunere în factori
• Factor comun: $3x^2 + 6x = 3x(x + 2)$.
• Formule: $x^2 - 9 = (x - 3)(x + 3)$; $x^2 - 6x + 9 = (x - 3)^2$.
• Trinomul $x^2 + sx + p$: cauți două numere cu suma $s$ și produsul $p$: $x^2 + 5x + 6 = (x + 2)(x + 3)$.

Rapoarte (fracții) algebrice
• Condiții de existență: numitorul nu poate fi $0$. Le scrii la început.
• Simplificare: descompui numărătorul și numitorul, apoi simplifici factorii comuni.
• Adunare/scădere: aduci la numitor comun (de obicei produsul factorilor diferiți).
• Împărțire: înmulțești cu inversa fracției.

Ce cere de obicei
• a) „Arată că $E(x) = \ldots$”: pornești de la stânga și transformi până obții dreapta.
• b) „$E(n)$ este număr natural”: numitorul trebuie să fie un divizor al numărătorului; „rezolvă $E(x) = \ldots$”; sau „arată că $E(n) > k$”.$EN$
WHERE code = 'A10' AND notes IS NULL;
INSERT INTO en_chapter_examples (chapter_id, position, statement, solution)
SELECT c.id, e.position, e.statement, e.solution
FROM en_chapters c
CROSS JOIN (VALUES
    (1, $EN$Arată că $x^2 - 6x + 9 = (x - 3)^2$.$EN$,
        $EN$$(x - 3)^2 = x^2 - 2 \cdot x \cdot 3 + 3^2 = x^2 - 6x + 9$. ✓$EN$),
    (2, $EN$Arată că $\frac{1}{x - 2} + \frac{1}{x + 2} = \frac{2x}{x^2 - 4}$, pentru $x \neq \pm 2$.$EN$,
        $EN$Numitorul comun: $(x - 2)(x + 2) = x^2 - 4$.
$\frac{x + 2}{x^2 - 4} + \frac{x - 2}{x^2 - 4} = \frac{x + 2 + x - 2}{x^2 - 4} = \frac{2x}{x^2 - 4}$. ✓$EN$),
    (3, $EN$Simplifică $\frac{x^2 - 9}{x^2 + 3x}$, pentru $x \neq 0$ și $x \neq -3$.$EN$,
        $EN$Numărătorul: $x^2 - 9 = (x - 3)(x + 3)$. Numitorul: $x^2 + 3x = x(x + 3)$.
$\frac{(x - 3)(x + 3)}{x(x + 3)} = \frac{x - 3}{x}$.$EN$),
    (4, $EN$Arată că $E(x) = (x + 1)^2 - (x - 1)(x + 1) - 2x$ nu depinde de $x$.$EN$,
        $EN$$(x + 1)^2 = x^2 + 2x + 1$ și $(x - 1)(x + 1) = x^2 - 1$.
$E(x) = x^2 + 2x + 1 - x^2 + 1 - 2x = 2$.
$E(x) = 2$ pentru orice $x$, deci nu depinde de $x$.$EN$),
    (5, $EN$Aflați numerele întregi $n$ pentru care $\frac{6}{n + 1}$ este număr natural.$EN$,
        $EN$Fracția e număr natural doar dacă $n + 1$ este divizor pozitiv al lui $6$:
$n + 1 \in \{1, 2, 3, 6\}$, deci $n \in \{0, 1, 2, 5\}$.
(Un divizor negativ ar da un rezultat negativ, care nu e natural.)$EN$)
) AS e (position, statement, solution)
WHERE c.code = 'A10'
  AND NOT EXISTS (SELECT 1 FROM en_chapter_examples x WHERE x.chapter_id = c.id);

-- A11
UPDATE en_chapters SET notes = $EN$Funcția liniară (Subiectul III, problema 3)

• $f : \mathbb{R} \to \mathbb{R}$, $f(x) = ax + b$. Graficul este o dreaptă.
• $f(m)$: înlocuiești $x$ cu $m$.
• Un punct e pe grafic: $A(m, n) \in G_f \Leftrightarrow f(m) = n$.

Intersecția graficului cu axele
• Cu $Ox$: punem $y = 0$, adică $f(x) = 0$ → $A\left(-\frac{b}{a}, 0\right)$.
• Cu $Oy$: punem $x = 0$ → $B(0, b)$.
• Ca să trasezi graficul, unești aceste două puncte.

Aflarea funcției
• Dacă știi două puncte de pe grafic, scrii $f(m_1) = n_1$ și $f(m_2) = n_2$ și rezolvi sistemul în $a$ și $b$.

Calcule cu triunghiul format de grafic și axe
• Distanța dintre două puncte: $AB = \sqrt{(x_B - x_A)^2 + (y_B - y_A)^2}$.
• Aria triunghiului $AOB$ (dreptunghic în $O$): $\frac{OA \cdot OB}{2}$ — lungimile sunt pozitive, ia modulul coordonatelor.
• Distanța de la $O$ la dreapta $AB$ = înălțimea din $O$: $d = \frac{2 \cdot \text{Aria}}{AB}$.$EN$
WHERE code = 'A11' AND notes IS NULL;
INSERT INTO en_chapter_examples (chapter_id, position, statement, solution)
SELECT c.id, e.position, e.statement, e.solution
FROM en_chapters c
CROSS JOIN (VALUES
    (1, $EN$Fie $f(x) = 3x - 6$. Calculați $f(1) + f(2)$.$EN$,
        $EN$$f(1) = 3 - 6 = -3$
$f(2) = 6 - 6 = 0$
$f(1) + f(2) = -3$.$EN$),
    (2, $EN$Fie $f(x) = 3x - 6$. Aflați punctele de intersecție ale graficului cu axele.$EN$,
        $EN$Cu $Ox$: $3x - 6 = 0 \Rightarrow x = 2$, deci $A(2, 0)$.
Cu $Oy$: $f(0) = -6$, deci $B(0, -6)$.$EN$),
    (3, $EN$Cu punctele $A(2, 0)$ și $B(0, -6)$ de mai sus, calculați aria triunghiului $AOB$ și distanța de la $O$ la dreapta $AB$.$EN$,
        $EN$$OA = 2$, $OB = 6$, triunghiul e dreptunghic în $O$:
Aria $= \frac{2 \cdot 6}{2} = 6$.
$AB = \sqrt{2^2 + 6^2} = \sqrt{40} = 2\sqrt{10}$.
$d = \frac{2 \cdot 6}{2\sqrt{10}} = \frac{6}{\sqrt{10}} = \frac{6\sqrt{10}}{10} = \frac{3\sqrt{10}}{5}$.$EN$),
    (4, $EN$Punctul $A(1, 5)$ aparține graficului funcției $f(x) = ax + 2$. Aflați $a$.$EN$,
        $EN$$f(1) = 5 \Rightarrow a \cdot 1 + 2 = 5 \Rightarrow a = 3$.$EN$),
    (5, $EN$Aflați funcția $f(x) = ax + b$ al cărei grafic trece prin $A(1, 1)$ și $B(3, 5)$.$EN$,
        $EN$$f(1) = 1$: $a + b = 1$
$f(3) = 5$: $3a + b = 5$
Scădem prima relație din a doua: $2a = 4 \Rightarrow a = 2$, apoi $b = -1$.
$f(x) = 2x - 1$.$EN$)
) AS e (position, statement, solution)
WHERE c.code = 'A11'
  AND NOT EXISTS (SELECT 1 FROM en_chapter_examples x WHERE x.chapter_id = c.id);

-- G1
UPDATE en_chapters SET notes = $EN$Segmente

• Puncte coliniare: se află pe aceeași dreaptă.
• Dacă $A$, $B$, $C$ sunt coliniare în această ordine, atunci $AC = AB + BC$.
• Segmente congruente: au aceeași lungime ($AB \equiv CD$).
• Mijlocul $M$ al segmentului $AB$: $M \in AB$ și $AM = MB = \frac{AB}{2}$.
• Simetricul punctului $A$ față de $B$ este punctul $C$ pentru care $B$ e mijlocul lui $AC$; atunci $AC = 2 \cdot AB$.

Cum lucrezi
• Desenează dreapta și pune punctele în ordinea din enunț.
• Notează pe desen lungimile cunoscute și scrie relațiile de adunare.
• Când apar „părți egale” (de exemplu $AB = BC = CD$), notează lungimea unei părți cu $x$.

Atenție
• „În această ordine” contează: fără ea, punctul $C$ ar putea fi și între $A$ și $B$.
• Unitățile de măsură trebuie să fie aceleași ($1$ dm $= 10$ cm).$EN$
WHERE code = 'G1' AND notes IS NULL;
INSERT INTO en_chapter_examples (chapter_id, position, statement, solution)
SELECT c.id, e.position, e.statement, e.solution
FROM en_chapters c
CROSS JOIN (VALUES
    (1, $EN$Punctele $A$, $B$, $C$ sunt coliniare, în această ordine, cu $AB = 4$ cm și $BC = 2 \cdot AB$. Calculați $AC$.$EN$,
        $EN$$BC = 2 \cdot 4 = 8$ cm.
$AC = AB + BC = 4 + 8 = 12$ cm.$EN$),
    (2, $EN$Punctul $C$ este simetricul lui $A$ față de $B$, iar $AB = 3$ cm. Calculați $AC$.$EN$,
        $EN$$B$ este mijlocul lui $AC$, deci $BC = AB = 3$ cm.
$AC = 2 \cdot 3 = 6$ cm.$EN$),
    (3, $EN$Punctele $A$, $B$, $C$, $D$ sunt coliniare, în această ordine, cu $AB = BC = CD$ și $AD = 18$ cm. Calculați $AC$.$EN$,
        $EN$Fie $AB = BC = CD = x$. Atunci $AD = 3x = 18$, deci $x = 6$ cm.
$AC = AB + BC = 2x = 12$ cm.$EN$),
    (4, $EN$Punctele $A$, $B$, $C$ sunt coliniare, în această ordine, cu $AB = 6$ cm și $BC = 10$ cm. $M$ este mijlocul lui $AB$, iar $N$ mijlocul lui $BC$. Calculați $MN$.$EN$,
        $EN$$MB = \frac{6}{2} = 3$ cm și $BN = \frac{10}{2} = 5$ cm.
$M$ și $N$ sunt de o parte și de alta a lui $B$: $MN = MB + BN = 8$ cm.$EN$)
) AS e (position, statement, solution)
WHERE c.code = 'G1'
  AND NOT EXISTS (SELECT 1 FROM en_chapter_examples x WHERE x.chapter_id = c.id);

-- G2
UPDATE en_chapters SET notes = $EN$Unghiuri

• Unghiuri complementare: suma lor este $90^\circ$. Suplementare: suma este $180^\circ$.
• Unghiuri adiacente: au vârful comun, o latură comună și sunt de o parte și de alta a ei.
• Adiacente suplementare: laturile necomune sunt semidrepte opuse (formează o dreaptă).
• Unghiuri opuse la vârf: sunt congruente.
• Unghiurile în jurul unui punct au suma $360^\circ$.
• Bisectoarea împarte unghiul în două unghiuri congruente: dacă $OM$ e bisectoarea lui $\angle AOB$, atunci $\angle AOM = \angle MOB = \frac{\angle AOB}{2}$.
• Drepte perpendiculare: formează un unghi de $90^\circ$.

Două drepte paralele tăiate de o secantă
• Unghiurile alterne interne sunt congruente; la fel cele alterne externe și cele corespondente.
• Unghiurile interne de aceeași parte a secantei sunt suplementare (suma $180^\circ$).

Cum lucrezi
• Notează unghiul necunoscut cu $x$ și scrie relația (sumă $90^\circ$, $180^\circ$ sau $360^\circ$).
• „De trei ori mai mare” → $3x$; „cu $20^\circ$ mai mare” → $x + 20^\circ$.$EN$
WHERE code = 'G2' AND notes IS NULL;
INSERT INTO en_chapter_examples (chapter_id, position, statement, solution)
SELECT c.id, e.position, e.statement, e.solution
FROM en_chapters c
CROSS JOIN (VALUES
    (1, $EN$Unghiurile $\angle AOB$ și $\angle BOC$ sunt adiacente suplementare, iar $\angle AOB = 50^\circ$. Calculați $\angle BOC$.$EN$,
        $EN$Suplementare: $\angle AOB + \angle BOC = 180^\circ$.
$\angle BOC = 180^\circ - 50^\circ = 130^\circ$.$EN$),
    (2, $EN$Unghiurile $\angle AOB$ și $\angle BOC$ sunt adiacente, $\angle BOC = 3 \cdot \angle AOB$, iar $\angle AOC = 120^\circ$. Calculați $\angle AOB$.$EN$,
        $EN$Fie $\angle AOB = x$; atunci $\angle BOC = 3x$.
$x + 3x = 120^\circ \Rightarrow 4x = 120^\circ \Rightarrow x = 30^\circ$.$EN$),
    (3, $EN$Calculați complementul și suplementul unui unghi de $35^\circ$.$EN$,
        $EN$Complementul: $90^\circ - 35^\circ = 55^\circ$.
Suplementul: $180^\circ - 35^\circ = 145^\circ$.$EN$),
    (4, $EN$Trei unghiuri formate în jurul unui punct au măsurile proporționale cu $2$, $3$ și $4$. Aflați măsurile lor.$EN$,
        $EN$Unghiurile sunt $2x$, $3x$, $4x$ și au suma $360^\circ$:
$9x = 360^\circ \Rightarrow x = 40^\circ$.
Măsurile: $80^\circ$, $120^\circ$, $160^\circ$.$EN$),
    (5, $EN$Dreptele $a$ și $b$ sunt paralele și sunt tăiate de secanta $c$. Unul dintre unghiurile alterne interne are $70^\circ$. Ce măsură au celălalt unghi alterne intern și unghiul intern de aceeași parte a secantei cu el?$EN$,
        $EN$Alterne interne → congruente: $70^\circ$.
Interne de aceeași parte → suplementare: $180^\circ - 70^\circ = 110^\circ$.$EN$)
) AS e (position, statement, solution)
WHERE c.code = 'G2'
  AND NOT EXISTS (SELECT 1 FROM en_chapter_examples x WHERE x.chapter_id = c.id);

-- G3
UPDATE en_chapters SET notes = $EN$Triunghiul

Unghiurile
• Suma unghiurilor unui triunghi este $180^\circ$.
• Unghiul exterior este egal cu suma celor două unghiuri interioare neadiacente cu el.

Triunghiuri speciale
• Isoscel ($AB = AC$): unghiurile de la bază sunt congruente ($\angle B = \angle C$); înălțimea din $A$ este și mediană, și bisectoare.
• Echilateral: toate laturile congruente, toate unghiurile de $60^\circ$.

Linii importante
• Bisectoarea: împarte unghiul în două. Mediana: unește un vârf cu mijlocul laturii opuse. Înălțimea: perpendiculara din vârf pe latura opusă. Mediatoarea: perpendiculara pe mijlocul unei laturi.

Congruența triunghiurilor
• Cazurile: LUL, ULU, LLL (și, la triunghiul dreptunghic: IC, IU, CC, CU).

Aria triunghiului
• $\mathcal{A} = \frac{b \cdot h}{2}$ (baza și înălțimea corespunzătoare ei).
• Mediana împarte triunghiul în două triunghiuri cu arii egale.
• Triunghiurile care au aceeași înălțime au ariile în același raport ca bazele.$EN$
WHERE code = 'G3' AND notes IS NULL;
INSERT INTO en_chapter_examples (chapter_id, position, statement, solution)
SELECT c.id, e.position, e.statement, e.solution
FROM en_chapters c
CROSS JOIN (VALUES
    (1, $EN$În triunghiul isoscel $ABC$ cu $AB = AC$, avem $\angle B = 50^\circ$. Calculați $\angle A$.$EN$,
        $EN$Unghiurile de la bază sunt congruente: $\angle C = \angle B = 50^\circ$.
$\angle A = 180^\circ - 50^\circ - 50^\circ = 80^\circ$.$EN$),
    (2, $EN$În triunghiul $ABC$, $\angle A = 40^\circ$ și $\angle B = 60^\circ$. Calculați unghiul exterior din $C$.$EN$,
        $EN$Unghiul exterior din $C$ este suma unghiurilor neadiacente: $40^\circ + 60^\circ = 100^\circ$.
(Verificare: $\angle C = 80^\circ$, iar $180^\circ - 80^\circ = 100^\circ$.)$EN$),
    (3, $EN$Aria triunghiului $ABC$ este $24$ cm², iar $M$ este mijlocul lui $BC$. Calculați aria triunghiului $ABM$.$EN$,
        $EN$$AM$ este mediană, deci împarte triunghiul în două triunghiuri de arii egale:
$\mathcal{A}_{ABM} = \frac{24}{2} = 12$ cm².$EN$),
    (4, $EN$Punctul $M$ se află pe latura $BC$, cu $BM = 2 \cdot MC$, iar aria triunghiului $ABC$ este $30$ cm². Calculați aria triunghiului $AMC$.$EN$,
        $EN$Triunghiurile $ABM$ și $AMC$ au aceeași înălțime (din $A$), deci ariile sunt în raportul bazelor $BM : MC = 2 : 1$.
Aria lui $AMC$ este o treime din total: $\frac{30}{3} = 10$ cm².$EN$)
) AS e (position, statement, solution)
WHERE c.code = 'G3'
  AND NOT EXISTS (SELECT 1 FROM en_chapter_examples x WHERE x.chapter_id = c.id);

-- G4
UPDATE en_chapters SET notes = $EN$Triunghiul dreptunghic (Pitagora, relații metrice)

În triunghiul $ABC$ dreptunghic în $A$ ($BC$ ipotenuza, $AB$ și $AC$ catetele, $AD \perp BC$):

• Teorema lui Pitagora: $BC^2 = AB^2 + AC^2$. Reciproca: dacă relația are loc, triunghiul e dreptunghic.
• Teorema înălțimii: $AD^2 = BD \cdot DC$.
• Teorema catetei: $AB^2 = BD \cdot BC$ și $AC^2 = DC \cdot BC$.
• Înălțimea: $AD = \frac{AB \cdot AC}{BC}$.
• Mediana pe ipotenuză este jumătate din ipotenuză: $AM = \frac{BC}{2}$.
• Cateta opusă unghiului de $30^\circ$ este jumătate din ipotenuză.

Funcții trigonometrice (pentru unghiul $B$)
• $\sin B = \frac{AC}{BC}$ (cateta opusă / ipotenuză)
• $\cos B = \frac{AB}{BC}$ (cateta alăturată / ipotenuză)
• $\operatorname{tg} B = \frac{AC}{AB}$ (cateta opusă / cateta alăturată)
• $\sin 30^\circ = \frac{1}{2}$, $\sin 45^\circ = \frac{\sqrt{2}}{2}$, $\sin 60^\circ = \frac{\sqrt{3}}{2}$.

De reținut
• Diagonala pătratului de latură $l$: $l\sqrt{2}$.
• Înălțimea triunghiului echilateral de latură $l$: $\frac{l\sqrt{3}}{2}$; aria lui: $\frac{l^2\sqrt{3}}{4}$.
• Triplete pitagoreice: $3, 4, 5$; $6, 8, 10$; $5, 12, 13$.$EN$
WHERE code = 'G4' AND notes IS NULL;
INSERT INTO en_chapter_examples (chapter_id, position, statement, solution)
SELECT c.id, e.position, e.statement, e.solution
FROM en_chapters c
CROSS JOIN (VALUES
    (1, $EN$Un triunghi dreptunghic are catetele de $6$ cm și $8$ cm. Calculați ipotenuza și înălțimea corespunzătoare ipotenuzei.$EN$,
        $EN$Pitagora: $BC^2 = 6^2 + 8^2 = 36 + 64 = 100 \Rightarrow BC = 10$ cm.
Înălțimea: $AD = \frac{6 \cdot 8}{10} = 4{,}8$ cm.$EN$),
    (2, $EN$În triunghiul $ABC$ dreptunghic în $A$, $\angle B = 30^\circ$ și $BC = 12$ cm. Calculați $AC$ și $AB$.$EN$,
        $EN$$AC$ este cateta opusă unghiului de $30^\circ$: $AC = \frac{12}{2} = 6$ cm.
Pitagora: $AB^2 = 12^2 - 6^2 = 144 - 36 = 108 \Rightarrow AB = 6\sqrt{3}$ cm.$EN$),
    (3, $EN$Calculați înălțimea unui triunghi echilateral cu latura de $6$ cm.$EN$,
        $EN$$h = \frac{l\sqrt{3}}{2} = \frac{6\sqrt{3}}{2} = 3\sqrt{3}$ cm.$EN$),
    (4, $EN$Triunghiul $ABC$ este dreptunghic în $A$, $AD \perp BC$, $BD = 4$ cm și $DC = 9$ cm. Calculați $AD$ și $AB$.$EN$,
        $EN$Teorema înălțimii: $AD^2 = BD \cdot DC = 4 \cdot 9 = 36 \Rightarrow AD = 6$ cm.
Teorema catetei: $AB^2 = BD \cdot BC = 4 \cdot 13 = 52 \Rightarrow AB = 2\sqrt{13}$ cm.$EN$)
) AS e (position, statement, solution)
WHERE c.code = 'G4'
  AND NOT EXISTS (SELECT 1 FROM en_chapter_examples x WHERE x.chapter_id = c.id);

-- G5
UPDATE en_chapters SET notes = $EN$Patrulatere

Paralelogramul
• Laturile opuse sunt paralele și congruente; unghiurile opuse sunt congruente; două unghiuri alăturate sunt suplementare.
• Diagonalele se înjumătățesc (se taie în mijlocul lor).
• Aria: $\mathcal{A} = b \cdot h$. O diagonală îl împarte în două triunghiuri cu arii egale.

Dreptunghiul, rombul, pătratul
• Dreptunghi: toate unghiurile drepte, diagonalele congruente. $P = 2(L + l)$, $\mathcal{A} = L \cdot l$.
• Romb: toate laturile congruente, diagonalele perpendiculare. $\mathcal{A} = \frac{d_1 \cdot d_2}{2}$; latura se află cu Pitagora din jumătățile diagonalelor.
• Pătrat: $P = 4l$, $\mathcal{A} = l^2$, diagonala $l\sqrt{2}$.

Trapezul
• Are două laturi paralele (bazele $B$ și $b$).
• Isoscel: laturile neparalele congruente, unghiurile de la aceeași bază congruente, diagonalele congruente.
• Linia mijlocie: $\frac{B + b}{2}$.
• Aria: $\mathcal{A} = \frac{(B + b) \cdot h}{2}$.
• În trapezul isoscel, proiecția laturii neparalele pe baza mare este $\frac{B - b}{2}$ — de aici afli înălțimea cu Pitagora.

Cum lucrezi
• Desenează figura, marchează ce e congruent sau paralel și caută triunghiuri dreptunghice în care să aplici Pitagora.$EN$
WHERE code = 'G5' AND notes IS NULL;
INSERT INTO en_chapter_examples (chapter_id, position, statement, solution)
SELECT c.id, e.position, e.statement, e.solution
FROM en_chapters c
CROSS JOIN (VALUES
    (1, $EN$Un dreptunghi are lungimea de trei ori mai mare decât lățimea și perimetrul de $32$ cm. Calculați aria lui.$EN$,
        $EN$Fie lățimea $l$; lungimea este $3l$.
$P = 2(3l + l) = 8l = 32 \Rightarrow l = 4$ cm, $L = 12$ cm.
$\mathcal{A} = 12 \cdot 4 = 48$ cm².$EN$),
    (2, $EN$Un pătrat are perimetrul de $24$ cm. Calculați diagonala lui.$EN$,
        $EN$Latura: $24 : 4 = 6$ cm.
Diagonala: $6\sqrt{2}$ cm.$EN$),
    (3, $EN$Un trapez are bazele de $10$ cm și $6$ cm și înălțimea de $4$ cm. Calculați aria și linia mijlocie.$EN$,
        $EN$$\mathcal{A} = \frac{(10 + 6) \cdot 4}{2} = 32$ cm².
Linia mijlocie: $\frac{10 + 6}{2} = 8$ cm.$EN$),
    (4, $EN$Un romb are diagonalele de $6$ cm și $8$ cm. Calculați aria și latura lui.$EN$,
        $EN$$\mathcal{A} = \frac{6 \cdot 8}{2} = 24$ cm².
Diagonalele sunt perpendiculare și se înjumătățesc: jumătățile au $3$ cm și $4$ cm.
Latura: $\sqrt{3^2 + 4^2} = 5$ cm.$EN$),
    (5, $EN$Paralelogramul $ABCD$ are aria de $60$ cm². Calculați aria triunghiului $ABC$.$EN$,
        $EN$Diagonala $AC$ împarte paralelogramul în două triunghiuri cu arii egale:
$\mathcal{A}_{ABC} = \frac{60}{2} = 30$ cm².$EN$)
) AS e (position, statement, solution)
WHERE c.code = 'G5'
  AND NOT EXISTS (SELECT 1 FROM en_chapter_examples x WHERE x.chapter_id = c.id);

-- G6
UPDATE en_chapters SET notes = $EN$Cercul

• Raza $r$; diametrul $d = 2r$ (cea mai lungă coardă).
• Coarda unește două puncte ale cercului; arcul este porțiunea de cerc dintre ele.

Unghiuri în cerc
• Unghiul la centru are aceeași măsură ca arcul cuprins între laturile lui.
• Unghiul înscris (vârful pe cerc) are jumătate din măsura arcului cuprins.
• Unghiul înscris într-un semicerc (care se sprijină pe un diametru) are $90^\circ$.
• Într-un patrulater înscris în cerc, unghiurile opuse sunt suplementare.

Coarde, tangente
• Coarde congruente ↔ arce congruente.
• Tangenta într-un punct este perpendiculară pe raza dusă în acel punct.
• Cele două tangente duse dintr-un punct exterior sunt congruente.

Lungimi și arii
• Lungimea cercului: $L = 2\pi r$. Aria discului: $\mathcal{A} = \pi r^2$.
• Un poligon regulat cu $n$ laturi înscris în cerc împarte cercul în $n$ arce de $\frac{360^\circ}{n}$ fiecare.$EN$
WHERE code = 'G6' AND notes IS NULL;
INSERT INTO en_chapter_examples (chapter_id, position, statement, solution)
SELECT c.id, e.position, e.statement, e.solution
FROM en_chapters c
CROSS JOIN (VALUES
    (1, $EN$Un unghi înscris în cerc cuprinde între laturile lui un arc de $80^\circ$. Ce măsură are unghiul?$EN$,
        $EN$Unghiul înscris are jumătate din arc: $\frac{80^\circ}{2} = 40^\circ$.$EN$),
    (2, $EN$$AB$ este diametru al cercului, iar $C$ un punct pe cerc, diferit de $A$ și $B$. Cât măsoară $\angle ACB$?$EN$,
        $EN$$\angle ACB$ este înscris și se sprijină pe diametru (cuprinde un semicerc de $180^\circ$):
$\angle ACB = \frac{180^\circ}{2} = 90^\circ$.$EN$),
    (3, $EN$Calculați lungimea și aria unui cerc cu raza de $5$ cm.$EN$,
        $EN$$L = 2\pi \cdot 5 = 10\pi$ cm.
$\mathcal{A} = \pi \cdot 5^2 = 25\pi$ cm².$EN$),
    (4, $EN$Triunghiul echilateral $ABC$ este înscris într-un cerc, iar $D$ este un punct pe arcul mic $BC$. Calculați $\angle BDC$.$EN$,
        $EN$$ABDC$ este un patrulater înscris, deci unghiurile opuse $A$ și $D$ sunt suplementare.
$\angle BDC = 180^\circ - \angle A = 180^\circ - 60^\circ = 120^\circ$.$EN$),
    (5, $EN$Dintr-un punct $P$ aflat la $13$ cm de centrul $O$ al unui cerc cu raza de $5$ cm se duce tangenta $PT$ ($T$ pe cerc). Calculați $PT$.$EN$,
        $EN$Tangenta este perpendiculară pe rază: triunghiul $OTP$ e dreptunghic în $T$.
$PT^2 = 13^2 - 5^2 = 169 - 25 = 144 \Rightarrow PT = 12$ cm.$EN$)
) AS e (position, statement, solution)
WHERE c.code = 'G6'
  AND NOT EXISTS (SELECT 1 FROM en_chapter_examples x WHERE x.chapter_id = c.id);

-- G7
UPDATE en_chapters SET notes = $EN$Asemănarea triunghiurilor și teorema lui Thales

Teorema lui Thales
• O paralelă la o latură a unui triunghi determină pe celelalte două laturi segmente proporționale: dacă $MN \parallel BC$ ($M \in AB$, $N \in AC$), atunci $\frac{AM}{MB} = \frac{AN}{NC}$.
• Reciproca: dacă segmentele sunt proporționale, atunci $MN \parallel BC$.

Teorema fundamentală a asemănării
• O paralelă la o latură formează un triunghi asemenea cu cel dat: $MN \parallel BC \Rightarrow \triangle AMN \sim \triangle ABC$, deci $\frac{AM}{AB} = \frac{AN}{AC} = \frac{MN}{BC}$.

Criterii de asemănare
• UU: două unghiuri congruente.
• LUL: două laturi proporționale și unghiul dintre ele congruent.
• LLL: toate laturile proporționale.
• Raportul de asemănare $k$ = raportul laturilor omoloage; raportul ariilor este $k^2$.

Linia mijlocie
• În triunghi: unește mijloacele a două laturi, este paralelă cu a treia și are jumătate din ea.
• În trapez: $\frac{B + b}{2}$.

Alte rezultate des folosite
• Diagonalele trapezului se taie într-un punct $O$ cu $\frac{AO}{OC} = \frac{BO}{OD} = \frac{B}{b}$ (triunghiurile $AOB$ și $COD$ sunt asemenea).
• Centrul de greutate $G$ se află pe fiecare mediană la $\frac{2}{3}$ de vârf și $\frac{1}{3}$ de bază.$EN$
WHERE code = 'G7' AND notes IS NULL;
INSERT INTO en_chapter_examples (chapter_id, position, statement, solution)
SELECT c.id, e.position, e.statement, e.solution
FROM en_chapters c
CROSS JOIN (VALUES
    (1, $EN$În triunghiul $ABC$, $M \in AB$, $N \in AC$, $MN \parallel BC$, $AM = 4$ cm, $MB = 6$ cm și $AN = 6$ cm. Calculați $NC$.$EN$,
        $EN$Teorema lui Thales: $\frac{AM}{MB} = \frac{AN}{NC} \Rightarrow \frac{4}{6} = \frac{6}{NC}$.
$4 \cdot NC = 36 \Rightarrow NC = 9$ cm.$EN$),
    (2, $EN$$MN$ este linie mijlocie în triunghiul $ABC$ ($M$, $N$ mijloacele lui $AB$ și $AC$), iar $BC = 14$ cm. Calculați $MN$.$EN$,
        $EN$Linia mijlocie are jumătate din latura paralelă cu ea: $MN = \frac{14}{2} = 7$ cm.$EN$),
    (3, $EN$În trapezul $ABCD$ cu $AB \parallel CD$, $AB = 12$ cm și $CD = 4$ cm, diagonalele se intersectează în $O$. Calculați $\frac{AO}{OC}$.$EN$,
        $EN$$AB \parallel CD$, deci $\angle OAB = \angle OCD$ și $\angle OBA = \angle ODC$ (alterne interne): $\triangle AOB \sim \triangle COD$ (UU).
$\frac{AO}{OC} = \frac{AB}{CD} = \frac{12}{4} = 3$.$EN$),
    (4, $EN$Triunghiurile $ABC$ și $DEF$ sunt asemenea, cu $AB = 6$ cm, $DE = 9$ cm și $BC = 8$ cm. Calculați $EF$ și raportul ariilor.$EN$,
        $EN$Raportul de asemănare: $k = \frac{DE}{AB} = \frac{9}{6} = \frac{3}{2}$.
$EF = \frac{3}{2} \cdot 8 = 12$ cm.
$\frac{\mathcal{A}_{DEF}}{\mathcal{A}_{ABC}} = k^2 = \frac{9}{4}$.$EN$),
    (5, $EN$$G$ este centrul de greutate al triunghiului $ABC$, iar mediana $AM = 12$ cm. Calculați $AG$ și $GM$.$EN$,
        $EN$$AG = \frac{2}{3} \cdot 12 = 8$ cm și $GM = \frac{1}{3} \cdot 12 = 4$ cm.$EN$)
) AS e (position, statement, solution)
WHERE c.code = 'G7'
  AND NOT EXISTS (SELECT 1 FROM en_chapter_examples x WHERE x.chapter_id = c.id);

-- G8
UPDATE en_chapters SET notes = $EN$Corpuri geometrice: arii și volume

Notații: $A_b$ aria bazei, $P_b$ perimetrul bazei, $A_l$ aria laterală, $A_t$ aria totală, $h$ înălțimea.

Cubul (muchia $l$)
• $V = l^3$, $A_t = 6l^2$, diagonala $d = l\sqrt{3}$.

Paralelipipedul dreptunghic ($L$, $l$, $h$)
• $V = L \cdot l \cdot h$, $A_t = 2(Ll + Lh + lh)$, diagonala $d = \sqrt{L^2 + l^2 + h^2}$.

Prisma dreaptă
• $A_l = P_b \cdot h$, $A_t = A_l + 2A_b$, $V = A_b \cdot h$.

Piramida regulată (apotema $a_p$ = înălțimea unei fețe laterale)
• $A_l = \frac{P_b \cdot a_p}{2}$, $A_t = A_l + A_b$, $V = \frac{A_b \cdot h}{3}$.
• Legătura: $a_p^2 = h^2 + a_b^2$, unde $a_b$ e apotema bazei (la baza pătrată, jumătate din latură).

Cilindrul (raza $R$, generatoarea $G = h$)
• $A_l = 2\pi R G$, $A_t = 2\pi R(G + R)$, $V = \pi R^2 G$.

Conul (raza $R$, generatoarea $G$, înălțimea $h$)
• $G^2 = h^2 + R^2$, $A_l = \pi R G$, $A_t = \pi R(G + R)$, $V = \frac{\pi R^2 h}{3}$.

Sfera (raza $R$)
• $A = 4\pi R^2$, $V = \frac{4\pi R^3}{3}$.

Atenție: $1$ dm³ $= 1$ litru $= 1000$ cm³.$EN$
WHERE code = 'G8' AND notes IS NULL;
INSERT INTO en_chapter_examples (chapter_id, position, statement, solution)
SELECT c.id, e.position, e.statement, e.solution
FROM en_chapters c
CROSS JOIN (VALUES
    (1, $EN$Un cub are muchia de $4$ cm. Calculați volumul, aria totală și diagonala.$EN$,
        $EN$$V = 4^3 = 64$ cm³.
$A_t = 6 \cdot 4^2 = 96$ cm².
$d = 4\sqrt{3}$ cm.$EN$),
    (2, $EN$Un paralelipiped dreptunghic are dimensiunile $3$ cm, $4$ cm și $12$ cm. Calculați diagonala și volumul.$EN$,
        $EN$$d = \sqrt{3^2 + 4^2 + 12^2} = \sqrt{9 + 16 + 144} = \sqrt{169} = 13$ cm.
$V = 3 \cdot 4 \cdot 12 = 144$ cm³.$EN$),
    (3, $EN$O prismă dreaptă are baza un triunghi echilateral cu latura de $6$ cm și înălțimea de $10$ cm. Calculați aria laterală și volumul.$EN$,
        $EN$$P_b = 3 \cdot 6 = 18$ cm, deci $A_l = 18 \cdot 10 = 180$ cm².
$A_b = \frac{6^2\sqrt{3}}{4} = 9\sqrt{3}$ cm², deci $V = 9\sqrt{3} \cdot 10 = 90\sqrt{3}$ cm³.$EN$),
    (4, $EN$O piramidă patrulateră regulată are latura bazei de $6$ cm și înălțimea de $4$ cm. Calculați apotema, aria laterală și volumul.$EN$,
        $EN$Apotema bazei este $\frac{6}{2} = 3$ cm.
$a_p = \sqrt{4^2 + 3^2} = 5$ cm.
$A_l = \frac{P_b \cdot a_p}{2} = \frac{24 \cdot 5}{2} = 60$ cm².
$V = \frac{A_b \cdot h}{3} = \frac{36 \cdot 4}{3} = 48$ cm³.$EN$),
    (5, $EN$Un con are raza $R = 4$ cm și generatoarea $G = 5$ cm. Calculați aria laterală și volumul.$EN$,
        $EN$$A_l = \pi R G = 20\pi$ cm².
Înălțimea: $h = \sqrt{5^2 - 4^2} = 3$ cm.
$V = \frac{\pi \cdot 16 \cdot 3}{3} = 16\pi$ cm³.$EN$)
) AS e (position, statement, solution)
WHERE c.code = 'G8'
  AND NOT EXISTS (SELECT 1 FROM en_chapter_examples x WHERE x.chapter_id = c.id);

-- G9
UPDATE en_chapters SET notes = $EN$Drepte și plane: unghiuri, perpendicularitate, distanțe

Poziții relative
• O dreaptă față de un plan: inclusă în plan, paralelă cu planul sau secantă (îl taie într-un punct).
• Două plane: paralele sau secante (se taie după o dreaptă).

Unghiul a două drepte
• Pentru drepte care nu se intersectează, duci o paralelă la una dintre ele printr-un punct al celeilalte; unghiul căutat este unghiul format de dreptele care se intersectează.

Perpendicularitate
• O dreaptă este perpendiculară pe un plan dacă este perpendiculară pe două drepte concurente din acel plan. Atunci e perpendiculară pe orice dreaptă din plan.
• Teorema celor trei perpendiculare: dacă $PA \perp \alpha$, $A \in \alpha$, iar $AB \perp d$ ($d \subset \alpha$, $B \in d$), atunci $PB \perp d$.

Distanțe
• Distanța de la un punct la un plan = lungimea perpendicularei din punct pe plan.
• Metoda volumului: într-un tetraedru, $V = \frac{\mathcal{A}_{baza} \cdot h}{3}$; calculezi volumul în două feluri și afli înălțimea dorită.

Unghiuri cu planul
• Unghiul dintre o dreaptă și un plan = unghiul dintre dreaptă și proiecția ei pe plan.
• Unghiul diedru (dintre două plane) = unghiul a două perpendiculare duse pe dreapta de intersecție, câte una în fiecare plan, din același punct.

În cub: fețele sunt pătrate, diagonalele fețelor sunt egale ($l\sqrt{2}$), iar o muchie verticală este perpendiculară pe planul bazei.$EN$
WHERE code = 'G9' AND notes IS NULL;
INSERT INTO en_chapter_examples (chapter_id, position, statement, solution)
SELECT c.id, e.position, e.statement, e.solution
FROM en_chapters c
CROSS JOIN (VALUES
    (1, $EN$În cubul $ABCDA'B'C'D'$, calculați măsura unghiului dreptelor $AC$ și $A'B$.$EN$,
        $EN$$AC \parallel A'C'$ (ACC'A' este dreptunghi), deci unghiul cerut este $\angle (A'C', A'B)$.
$A'C'$, $A'B$ și $BC'$ sunt diagonale ale unor fețe, deci sunt congruente: triunghiul $A'BC'$ e echilateral.
Unghiul este $60^\circ$.$EN$),
    (2, $EN$În cubul $ABCDA'B'C'D'$, arătați că $BD \perp (ACC')$.$EN$,
        $EN$$BD \perp AC$ (diagonalele pătratului $ABCD$ sunt perpendiculare).
$CC' \perp (ABCD)$, deci $CC' \perp BD$.
$BD$ este perpendiculară pe două drepte concurente din planul $(ACC')$, deci $BD \perp (ACC')$.$EN$),
    (3, $EN$Cubul $ABCDA'B'C'D'$ are muchia de $6$ cm. Calculați distanța de la $A$ la planul $(A'BD)$.$EN$,
        $EN$Folosim volumul tetraedrului $AA'BD$.
Cu baza $ABD$ și înălțimea $AA'$: $V = \frac{1}{3} \cdot \frac{6 \cdot 6}{2} \cdot 6 = 36$ cm³.
Triunghiul $A'BD$ e echilateral cu latura $6\sqrt{2}$: aria $= \frac{(6\sqrt{2})^2\sqrt{3}}{4} = 18\sqrt{3}$ cm².
$V = \frac{18\sqrt{3} \cdot d}{3} = 36 \Rightarrow d = \frac{108}{18\sqrt{3}} = \frac{6}{\sqrt{3}} = 2\sqrt{3}$ cm.$EN$),
    (4, $EN$O piramidă patrulateră regulată $VABCD$ are latura bazei de $6$ cm și înălțimea $VO = 3\sqrt{3}$ cm. Calculați unghiul dintre o față laterală și planul bazei.$EN$,
        $EN$Fie $M$ mijlocul lui $BC$. $OM \perp BC$ și $VM \perp BC$, deci unghiul diedru este $\angle VMO$.
$OM = \frac{6}{2} = 3$ cm, iar triunghiul $VOM$ e dreptunghic în $O$:
$\operatorname{tg} \angle VMO = \frac{VO}{OM} = \frac{3\sqrt{3}}{3} = \sqrt{3} \Rightarrow \angle VMO = 60^\circ$.$EN$)
) AS e (position, statement, solution)
WHERE c.code = 'G9'
  AND NOT EXISTS (SELECT 1 FROM en_chapter_examples x WHERE x.chapter_id = c.id);
