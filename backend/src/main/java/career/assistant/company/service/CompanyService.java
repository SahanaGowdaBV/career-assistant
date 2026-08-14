package career.assistant.company.service;

import career.assistant.company.entity.Company;
import career.assistant.company.repository.CompanyRepository;
import org.springframework.stereotype.Service;

@Service
public class CompanyService {

    private final CompanyRepository companyRepository;

    public CompanyService(CompanyRepository companyRepository) {
        this.companyRepository = companyRepository;
    }

    public Company findOrCreate(String companyName) {

        return companyRepository.findByNameIgnoreCase(companyName)
                .orElseGet(() -> {
                    Company company = new Company();
                    company.setName(companyName);
                    return companyRepository.save(company);
                });
    }
}
