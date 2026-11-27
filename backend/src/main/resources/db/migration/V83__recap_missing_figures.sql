-- „Teme recapitulative”: the import flagged a missing drawing only when the extraction had seen "(figura" or "[figura";
-- "În figura 1", "figura alăturată", "Figura 2 reprezintă" slipped through, and some of those exercises were approved
-- without their drawing. Flag every exercise without a picture whose text (or one of its requirements) cites a drawing:
-- it stays out of tests until a picture is added (a reload of the zip adds the ones found since) or a teacher waives it.
-- Same reading as RecapFigures: "figura/figură/figurii/figurile", "fig. 3"; not "figurează", "figurativă".
-- The transcription's notes in HTML comments ("No figures found") leave the statements first: they are not the book's.
UPDATE recap_exercises
SET statement = btrim(regexp_replace(statement, '\s*<!--.*?-->', '', 'g'))
WHERE statement LIKE '%<!--%';

UPDATE recap_exercise_parts
SET statement = btrim(regexp_replace(statement, '\s*<!--.*?-->', '', 'g'))
WHERE statement LIKE '%<!--%';

UPDATE recap_exercises e
SET figure_missing = TRUE
WHERE e.image_key IS NULL
  AND NOT e.figure_missing
  AND (e.statement ~* '\mfigur(a|ă|ii|ile|i)\M|\mfig\.\s*[0-9]'
       OR EXISTS (SELECT 1 FROM recap_exercise_parts p
                  WHERE p.exercise_id = e.id AND p.statement ~* '\mfigur(a|ă|ii|ile|i)\M|\mfig\.\s*[0-9]'));
