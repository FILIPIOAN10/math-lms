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
            Map.entry("A1", "„Rezultatul calculului…” cu numere naturale: ordinea operațiilor, paranteze, puteri"),
            Map.entry("A2", "raport, proporție a/b = c/d, termen necunoscut, mărimi direct/invers proporționale"),
            Map.entry("A3", "procente (p% din…, scumpire, reducere), fracție dintr-un număr (2/3 din 12)"),
            Map.entry("A4", "numere întregi: opusul, semne la înmulțire/împărțire, puteri cu bază negativă"),
            Map.entry("A5", "mulțimi, intervale (intersecție, reuniune), inecuații ax+b<0, divizibilitate, cel mai mare întreg dintr-un interval"),
            Map.entry("A6", "radicali, scoaterea factorilor, modul, media aritmetică a unor numere iraționale, fracții periodice, compararea numerelor reale"),
            Map.entry("A7", "ecuația ax + b = c, verificarea unei soluții"),
            Map.entry("A8", "diagramă, tabel, grafic cu date: citirea informațiilor, adevărat/fals"),
            Map.entry("A9", "problemă cu text rezolvată cu ecuații sau sisteme: vârste, prețuri, bănci, grupe cu rest"),
            Map.entry("A10", "expresia E(x) cu fracții algebrice: formule de calcul prescurtat, descompuneri, simplificări, E(n) natural"),
            Map.entry("A11", "funcția f(x) = ax + b: valori, graficul, intersecția cu axele, distanțe și arii în reper"),
            Map.entry("G1", "puncte coliniare, lungimi de segmente, mijlocul unui segment, simetricul unui punct"),
            Map.entry("G2", "unghiuri adiacente, complementare, suplementare, opuse la vârf, bisectoarea"),
            Map.entry("G3", "triunghi: suma unghiurilor, unghi exterior, isoscel, echilateral, linii importante, aria, congruență"),
            Map.entry("G4", "triunghi dreptunghic: Pitagora, unghiul de 30°, înălțimea, teorema catetei, sin, cos"),
            Map.entry("G5", "paralelogram, dreptunghi, romb, pătrat, trapez: perimetru, arie, diagonale"),
            Map.entry("G6", "cerc: arce, coarde, unghi la centru, unghi înscris, diametru, poligon înscris"),
            Map.entry("G7", "asemănarea triunghiurilor, teorema lui Thales, paralele, linia mijlocie, raport de arii"),
            Map.entry("G8", "cub, paralelipiped, prismă, piramidă, cilindru, con, sferă: arii și volume, diagonala cubului"),
            Map.entry("G9", "în spațiu: unghiul a două drepte, drepte și plane perpendiculare, distanța de la un punct la un plan"));
}
