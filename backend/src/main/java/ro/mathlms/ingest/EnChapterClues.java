package ro.mathlms.ingest;

import java.util.Map;

/**
 * How each seeded EN chapter is recognised in a statement (docs/EN-capitole.md), for the chapter suggester's prompt. A
 * chapter the teacher added has no clues: its title has to do.
 */
final class EnChapterClues {

    private EnChapterClues() {
    }

    static final Map<String, String> BY_CODE = Map.ofEntries(
            Map.entry("A1", "„Rezultatul calculului…” cu numere naturale: ordinea operațiilor, paranteze, puteri, împărțirea cu rest, compararea puterilor, scrierea în baza 2"),
            Map.entry("A2", "raport, proporție a/b = c/d, termen necunoscut, mărimi direct/invers proporționale, regula de trei simplă"),
            Map.entry("A3", "procente (p% din…, scumpire, reducere), fracție dintr-un număr (2/3 din 12)"),
            Map.entry("A4", "numere întregi: opusul, semne la înmulțire/împărțire, puteri cu bază negativă"),
            Map.entry("A5", "mulțimi, intervale (intersecție, reuniune), inecuații ax+b<0, divizibilitate, cel mai mare întreg dintr-un interval, cardinal, numere prime între ele"),
            Map.entry("A6", "radicali, scoaterea factorilor, modul, media aritmetică a unor numere iraționale, fracții periodice, compararea numerelor reale, media ponderată, media geometrică, puteri cu exponent negativ"),
            Map.entry("A7", "ecuația ax + b = c, verificarea unei soluții"),
            Map.entry("A8", "diagramă, tabel, grafic cu date: citirea informațiilor, adevărat/fals"),
            Map.entry("A9", "problemă cu text rezolvată cu ecuații sau sisteme: vârste, prețuri, bănci, grupe cu rest"),
            Map.entry("A10", "expresia E(x) cu fracții algebrice: formule de calcul prescurtat, descompuneri, simplificări, E(n) natural"),
            Map.entry("A11", "funcția f(x) = ax + b: valori, graficul, intersecția cu axele, distanțe și arii în reper, produs cartezian"),
            Map.entry("A12", "operații cu fracții ordinare și zecimale: numitor comun, amplificare, simplificare, scoaterea întregilor, ordonarea fracțiilor"),
            Map.entry("A13", "problemă rezolvată prin metode aritmetice: reducerea la unitate, comparație, figurativă, mersul invers, falsa ipoteză"),
            Map.entry("A14", "ecuația ax^2 + bx + c = 0: discriminant, descompunere, probleme care duc la o ecuație de gradul al II-lea"),
            Map.entry("A15", "statistică și probabilități: frecvență, mediană, mod, amplitudine, poligonul frecvențelor, cazuri favorabile / cazuri posibile"),
            Map.entry("G1", "puncte coliniare, lungimi de segmente, mijlocul unui segment, simetricul unui punct, transformări de unități de măsură"),
            Map.entry("G2", "unghiuri adiacente, complementare, suplementare, opuse la vârf, bisectoarea, calcule cu grade și minute"),
            Map.entry("G3", "triunghi: suma unghiurilor, unghi exterior, isoscel, echilateral, linii importante, aria, congruență, inegalitatea triunghiului, cerc înscris și circumscris"),
            Map.entry("G4", "triunghi dreptunghic: Pitagora, unghiul de 30°, înălțimea, teorema catetei, sin, cos, tg, ctg, latura și apotema hexagonului regulat"),
            Map.entry("G5", "paralelogram, dreptunghi, romb, pătrat, trapez: perimetru, arie, diagonale"),
            Map.entry("G6", "cerc: arce, coarde, unghi la centru, unghi înscris, diametru, poligon înscris, pozițiile unei drepte față de cerc, pozițiile a două cercuri"),
            Map.entry("G7", "asemănarea triunghiurilor, teorema lui Thales, paralele, linia mijlocie, raport de arii, paralele echidistante, împărțirea unui segment în părți proporționale"),
            Map.entry("G8", "cub, paralelipiped, prismă, piramidă, cilindru, con, sferă: arii și volume, diagonala cubului, tetraedru regulat, baza hexagon regulat"),
            Map.entry("G9", "în spațiu: unghiul a două drepte, drepte și plane perpendiculare, distanța de la un punct la un plan, distanța dintre plane paralele, proiecția unui segment pe un plan"),
            Map.entry("G10", "trunchi de piramidă sau de con, secțiune paralelă cu baza, secțiune diagonală sau axială, desfășurare, cel mai scurt drum pe suprafață"));
}
