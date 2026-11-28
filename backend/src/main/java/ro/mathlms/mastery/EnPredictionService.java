package ro.mathlms.mastery;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.mathlms.enchapter.EnChapterSlot;
import ro.mathlms.enchapter.EnChapterSlotRepository;
import ro.mathlms.user.User;

import java.util.HashMap;
import java.util.Map;

/** „Ce notă iei la EN?": the student's EN map ({@link EnMasteryService}) turned into an estimated grade. */
@Service
public class EnPredictionService {

    private final EnMasteryService masteryService;
    private final EnChapterSlotRepository slotRepository;

    public EnPredictionService(EnMasteryService masteryService, EnChapterSlotRepository slotRepository) {
        this.masteryService = masteryService;
        this.slotRepository = slotRepository;
    }

    @Transactional(readOnly = true)
    public EnPrediction predict(User student) {
        return EnPredictionCalculator.predict(masteryService.forStudent(student), examPoints());
    }

    /** Paper points per EN chapter code, from the places on the paper as the admin linked them. */
    @Transactional(readOnly = true)
    public Map<String, Double> examPoints() {
        Map<String, String> slotToCode = new HashMap<>();
        for (EnChapterSlot slot : slotRepository.findAllFetched()) {
            slotToCode.put(slot.getSlot(), slot.getChapter().getCode());
        }
        return EnPredictionCalculator.examPoints(slotToCode);
    }
}
