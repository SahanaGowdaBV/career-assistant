package career.assistant.document.service;

import career.assistant.api.ResourceNotFoundException;
import career.assistant.company.repository.CompanyRepository;
import career.assistant.document.entity.CoverLetter;
import career.assistant.document.entity.ResumeVersion;
import career.assistant.document.model.ParsedResume;
import career.assistant.document.model.ResumeDownload;
import career.assistant.document.repository.CoverLetterRepository;
import career.assistant.document.repository.ResumeVersionRepository;
import career.assistant.document.storage.ResumeStorage;
import career.assistant.document.storage.StoredResumeObject;
import career.assistant.job.entity.Job;
import career.assistant.jobscore.entity.JobScore;
import career.assistant.jobscore.repository.JobScoreRepository;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.io.ByteArrayOutputStream;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Service
public class CoverLetterService {
    public static final String DOCX="application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private final CoverLetterRepository letters; private final ResumeVersionRepository resumes; private final JobScoreRepository scores;
    private final CompanyRepository companies; private final ResumeJsonCodec json; private final ResumeStorage storage;
    public CoverLetterService(CoverLetterRepository l, ResumeVersionRepository r, JobScoreRepository s, CompanyRepository c, ResumeJsonCodec j, ResumeStorage st){letters=l;resumes=r;scores=s;companies=c;json=j;storage=st;}

    @Transactional public CoverLetter generate(Job job){
        var existing=letters.findFirstByJobId(job.getId()); if(existing.isPresent()) return existing.get();
        ResumeVersion master=resumes.findFirstByMasterResumeTrue().orElseThrow(()->new ResumeConflictException("An active verified master resume is required"));
        ParsedResume parsed=json.readResume(master.getStructuredExperience());
        JobScore score=scores.findByJob(job).orElseThrow(()->new ResumeConflictException("The job must be scored before package generation"));
        Set<String> verified=new TreeSet<>(String.CASE_INSENSITIVE_ORDER); verified.addAll(parsed.skills());
        List<String> matched=csv(score.getMatchedKeywords()).stream().filter(verified::contains).toList();
        String company=companies.findById(job.getCompanyId()).map(c->c.getName()).orElse("Hiring Team");
        String name=parsed.name()==null||parsed.name().isBlank()?"Candidate":parsed.name();
        String evidence=matched.isEmpty()?"my verified experience described in the attached resume":"my verified experience with "+String.join(", ",matched);
        String body="Dear "+company+" Hiring Team,\n\nI am applying for the "+job.getTitle()+" role in the UAE. I would bring "+evidence+".\n\nMy background, employers, titles, dates, certifications, and outcomes are presented exactly as verified in my resume. I am currently located in India, am open to relocating to the UAE, and have a 90-day notice period. I would welcome the opportunity to discuss how this verified experience aligns with your needs.\n\nSincerely,\n"+name;
        byte[] bytes=docx(body); String path=OffsetDateTime.now(ZoneOffset.UTC).getYear()+"/cover-letters/"+UUID.randomUUID()+".docx";
        CoverLetter letter=new CoverLetter(); letter.setJobId(job.getId()); letter.setTitle(job.getTitle()+" cover letter"); letter.setContent(body); letter.setFileName(safe(job.getTitle())+"-cover-letter.docx"); letter.setStoragePath(path); letter.setContentType(DOCX); letter.setCustomized(true); letter.setCustomizationSummary("UAE-focused letter using only verified resume facts and matched skills: "+String.join(", ",matched));
        storage.store(path,bytes,DOCX); try{return letters.save(letter);}catch(RuntimeException e){storage.delete(path);throw e;}
    }
    @Transactional(readOnly=true) public List<LetterResponse> list(){return letters.findAllByOrderByCreatedAtDesc().stream().map(LetterResponse::of).toList();}
    @Transactional(readOnly=true) public LetterResponse get(UUID id){return LetterResponse.of(required(id));}
    @Transactional(readOnly=true) public ResumeDownload download(UUID id){var l=required(id); StoredResumeObject o=storage.load(l.getStoragePath());return new ResumeDownload(o.content(),l.getContentType(),l.getFileName());}
    private CoverLetter required(UUID id){return letters.findById(id).orElseThrow(()->new ResourceNotFoundException("Cover letter not found"));}
    private static List<String> csv(String v){return v==null||v.isBlank()?List.of():Arrays.stream(v.split(",")).map(String::trim).filter(s->!s.isBlank()).toList();}
    private static String safe(String v){return v.replaceAll("[^A-Za-z0-9._-]+","-").replaceAll("^-|-$","");}
    private static byte[] docx(String text){try(var d=new XWPFDocument();var out=new ByteArrayOutputStream()){for(String p:text.split("\\n",-1))d.createParagraph().createRun().setText(p);d.write(out);return out.toByteArray();}catch(Exception e){throw new IllegalStateException("Could not create cover letter",e);}}
    public record LetterResponse(UUID id,UUID jobId,String title,String content,String fileName,String summary,OffsetDateTime createdAt){static LetterResponse of(CoverLetter l){return new LetterResponse(l.getId(),l.getJobId(),l.getTitle(),l.getContent(),l.getFileName(),l.getCustomizationSummary(),l.getCreatedAt());}}
}
