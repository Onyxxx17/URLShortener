package com.ayth.urlshortener.url;

import com.ayth.urlshortener.exception.UrlNotFoundException;
import com.ayth.urlshortener.util.ShortCodeGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ayth.urlshortener.url.URLRepository;

import java.util.List;
import java.util.Optional;

@Service
class URLService {

    private final URLRepository urlRepository;

    @Autowired
    public URLService(URLRepository urlRepository) {
        this.urlRepository = urlRepository;
    }

    public URL findByOriginalURL(String originalURL) {
        Optional<URL> optional = urlRepository.findByOriginalUrl(originalURL);
        return optional.orElseThrow(() -> new UrlNotFoundException("Url not found"));
    }

    public URL findById(Long id) {
        Optional<URL> optional = urlRepository.findById(id);
        return optional.orElseThrow(() -> new UrlNotFoundException("Url with id " + id + "not found"));
    }

    public URL findByShortURL(String shortURL) {
        //Find with redis first (Implement later)
        // {
        //
        // }
        Optional<URL> optional = urlRepository.findByShortCode(shortURL);
        return optional.orElseThrow(() -> new UrlNotFoundException("Url with short code " + shortURL + " not found"));
    }

    public URL addUrl(String originalUrl) {
        String shortCode;
        Optional<URL> optional = urlRepository.findByOriginalUrl(originalUrl);
        if(optional.isPresent()) {
            throw new UrlNotFoundException("URL already exists");
        }

        URL newURL = new URL();
        newURL.setOriginalUrl(originalUrl);

        //Generate short code from generator
        do{
            shortCode = ShortCodeGenerator.generateShortCode();
        }while (urlRepository.findByShortCode(newURL.getShortCode()).isPresent());


        //Add to redis (Implement Later)
        //

        newURL.setShortCode(shortCode);
        urlRepository.save(newURL);

        return newURL;
    }

    public void save(URL newURL) {
        urlRepository.save(newURL);
    }

    public void deleteById(Long id) {
        Optional<URL> optional = urlRepository.findById(id);
        if(optional.isEmpty()){
            throw new UrlNotFoundException("URL does not exist");
        }
        urlRepository.deleteById(id);
    }

    public void deleteByShortCode(String shortCode) {
        Optional<URL> optional = urlRepository.findByShortCode(shortCode);
        if(optional.isPresent()) {
            urlRepository.deleteByShortCode(shortCode);
        } else{
            throw new UrlNotFoundException("URL does not exist");
        }
    }

    public void deleteByOriginalURL(String originalURL) {
        Optional<URL> optional = urlRepository.findByOriginalUrl(originalURL);
        if(optional.isEmpty()){
            throw new UrlNotFoundException("URL does not exist");
        }
        urlRepository.deleteByOriginalUrl(originalURL);
    }

    public List<URL> findAll() {
        return urlRepository.findAll();
    }


}
