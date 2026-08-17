package career.assistant.company.controller;

import career.assistant.company.entity.Company;
import career.assistant.company.repository.CompanyRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/companies")
public class CompanyController {
  private final CompanyRepository repository;
  public CompanyController(CompanyRepository repository) { this.repository = repository; }
  @GetMapping public List<Company> list() { return repository.findAll(); }
  @PostMapping public Company create(@Valid @RequestBody CompanyRequest request) {
    if (repository.existsByNameIgnoreCase(request.name())) throw new IllegalArgumentException("Company already exists");
    Company c = new Company(); c.setName(request.name()); c.setWebsite(request.website()); c.setCareersUrl(request.careersUrl()); return repository.save(c);
  }
  public record CompanyRequest(@NotBlank @Size(max=255) String name, @Size(max=500) String website, @Size(max=500) String careersUrl) {}
}
