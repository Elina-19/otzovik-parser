package ru.itis.service.parser;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.itis.db.entity.Company;
import ru.itis.db.entity.Review;
import ru.itis.db.repositories.CompanyRepository;
import ru.itis.db.repositories.ReviewRepository;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class OtzovikParser {

    private final CompanyRepository companyRepository;
    private final ReviewRepository reviewRepository;

//    @Scheduled(cron = "${schedule.cron}")
    public void parse(String companyName) {
        Company company = companyRepository.getCompanyByName(companyName);

        List<String> urls = getUrls(company.getUrl());
        List<Review> reviews = urls.stream()
                .map(this::getDocument)
                .map(doc -> getReview(doc, company))
                .collect(Collectors.toList());

        reviewRepository.saveAll(reviews);
    }

    private Review getReview(Document document, Company company) {
        String name = document.getElementsByClass("element_name").first().text();
        String description = document.getElementsByClass("description").first().text();
        String date = getDate(document.getElementsByClass("dtreviewed").first().text());
        String styleRate = document.getElementsByClass("comment_header").first()
                .getElementsByClass("star_ring").first()
                .getElementsByTag("span").first()
                .getElementsByAttribute("style").first()
                .attr("style");
        Integer rate = Integer.valueOf(StringUtils.substringBetween(styleRate, ":", "px"))/13;

        return Review.builder()
                .sourceName(company.getName())
                .company(company)
                .date(date)
                .title(name)
                .description(description)
                .rate(rate)
                .build();
    }

    private String getDate(String date) {
        String resultDate = Optional.ofNullable(date)
                .filter(d -> !date.contains("год"))
                .map(d -> d + ", " + LocalDateTime.now().getYear() + "год")
                .map(d -> StringUtils.substringAfter(d, ", "))
                .orElse(StringUtils.EMPTY);

        return resultDate.isEmpty() ? date : resultDate;
    }

    private List<String> getUrls(String url) {
        List<Document> documents = new ArrayList<>();
        documents.add(getDocument(url));

        for (int i = 2; i < 6; i++) {
            documents.add(getDocument(url + "?page=" + i));
        }

        List<String> urls = new ArrayList<>();
        documents.stream()
                .map(this::getUrlsReviewFromDocument)
                .forEach(urls::addAll);

        return urls;
    }

    private List<String> getUrlsReviewFromDocument(Document document) {
        return document.getElementsByClass("comment_row").stream()
                .filter(el -> !el.getElementsByTag("h2").isEmpty())
                .map(el -> el.getElementsByTag("h2").first().getElementsByTag("a").first())
                .filter(Objects::nonNull)
                .map(el -> el.attr("href"))
                .collect(Collectors.toList());
    }

    private Document getDocument(String url) {
        try {
            Document document = Jsoup.connect(url).get();
            return document;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}
