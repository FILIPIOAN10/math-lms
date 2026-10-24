package ro.mathlms.content;

/** A school class as the API returns it. */
/** {@code teacherId}: who owns the class (null = only ADMINs see it); the admin's class page shows and changes it. */
public record SchoolClassDto(Long id, String name, String description, Long teacherId) {
    public static SchoolClassDto from(SchoolClass schoolClass) {
        return new SchoolClassDto(schoolClass.getId(), schoolClass.getName(),
                schoolClass.getDescription(), schoolClass.getTeacherId());
    }
}
