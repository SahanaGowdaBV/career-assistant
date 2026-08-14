package career.assistant.scraper.service;

import career.assistant.scraper.config.JobSource;
import career.assistant.scraper.model.ScrapedJob;

import java.util.List;

public interface JobScraper {

    JobSource getSource();

    List<ScrapedJob> scrape();
}