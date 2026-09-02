package ro.mathlms.content;

/** A school class as the API returns it. */
public record SchoolClassDto(Long id, String name, String description) {
    public static SchoolClassDto from(SchoolClass schoolClass) {
        return new SchoolClassDto(schoolClass.getId(), schoolClass.getName(),
                schoolClass.getDescription());
    }
}
