package career.assistant.document.entity;
import jakarta.persistence.*; import java.time.OffsetDateTime; import java.util.UUID;
@Entity @Table(name="cover_letters") public class CoverLetter {
 @Id @GeneratedValue private UUID id; @Column(nullable=false) private String title; @Column(nullable=false,columnDefinition="TEXT") private String content; private boolean customized; @Column(name="customization_summary",columnDefinition="TEXT") private String customizationSummary; @Column(name="created_at") private OffsetDateTime createdAt;
 @PrePersist void create(){if(createdAt==null)createdAt=OffsetDateTime.now();} public UUID getId(){return id;} public String getTitle(){return title;} public void setTitle(String v){title=v;} public String getContent(){return content;} public void setContent(String v){content=v;} public boolean isCustomized(){return customized;} public void setCustomized(boolean v){customized=v;} public String getCustomizationSummary(){return customizationSummary;} public void setCustomizationSummary(String v){customizationSummary=v;} public OffsetDateTime getCreatedAt(){return createdAt;}
}
