# Evaluare retrieval — 2026-10-16

Corpus: 42 de chunk-uri (capitolul I). Întrebări: 20 de concept, 4 în stil EN (cu locul din test), 6 din afara manualului. Embeddings: text-embedding-3-small.

Setul de aur e scris de un om după capitol (nu e măsurat pe elevi): cifrele sunt orientative.

## Toate întrebările la care manualul răspunde
| strategie | hit@1 | hit@3 | hit@5 | MRR | chunk-uri date |
|---|---|---|---|---|---|
| vector | 88% | 92% | 96% | 0.90 | 5.0 |
| fulltext | 38% | 54% | 67% | 0.47 | 5.0 |
| bm25 | 71% | 75% | 83% | 0.75 | 4.8 |
| hybrid | 79% | 88% | 92% | 0.83 | 5.0 |
| hybrid_2_1 | 79% | 88% | 92% | 0.83 | 5.0 |
| mmr | 88% | 88% | 96% | 0.89 | 5.0 |
| politica: loc din test, altfel vector | 96% | 100% | 100% | 0.98 | 4.5 |

## Doar întrebările de concept
| strategie | hit@1 | hit@3 | hit@5 | MRR | chunk-uri date |
|---|---|---|---|---|---|
| vector | 95% | 100% | 100% | 0.97 | 5.0 |
| fulltext | 45% | 65% | 70% | 0.53 | 5.0 |
| bm25 | 75% | 80% | 90% | 0.80 | 5.0 |
| hybrid | 85% | 95% | 95% | 0.89 | 5.0 |
| hybrid_2_1 | 85% | 95% | 95% | 0.89 | 5.0 |
| mmr | 95% | 95% | 95% | 0.95 | 5.0 |

## Doar itemurile în stil EN (au un loc în test)
| strategie | hit@1 | hit@3 | hit@5 | MRR | chunk-uri date |
|---|---|---|---|---|---|
| vector | 50% | 50% | 75% | 0.55 | 5.0 |
| fulltext | 0% | 0% | 50% | 0.12 | 5.0 |
| bm25 | 50% | 50% | 50% | 0.50 | 3.8 |
| hybrid | 50% | 50% | 75% | 0.55 | 5.0 |
| hybrid_2_1 | 50% | 50% | 75% | 0.55 | 5.0 |
| mmr | 50% | 50% | 100% | 0.60 | 5.0 |
| slot baseline | 100% | 100% | 100% | 1.00 | 2.0 |
| slot + vector | 100% | 100% | 100% | 1.00 | 2.0 |

## Refuzul: distanța celui mai apropiat chunk
- întrebări la care manualul răspunde: 0.25 – 0.46 (mediana 0.34)
- întrebări din afara manualului: 0.40 – 0.68 (mediana 0.54)
- cel mai bun prag: 0.465 → 97% corecte (răspunde sub prag, refuză peste; nu-l rotunji în jos: o întrebare acoperită stă chiar la 0.465)

## Unde a ratat (nicio bucată corectă în primele 5)
- vector: e02
- fulltext: c03, c04, c08, c09, c12, c20, e01, e04
- bm25: c07, c09, e02, e03
- hybrid: c09, e02
- hybrid_2_1: c09, e02
- mmr: c09
- slot baseline: nicăieri
- slot + vector: nicăieri
- politica: loc din test, altfel vector: nicăieri
