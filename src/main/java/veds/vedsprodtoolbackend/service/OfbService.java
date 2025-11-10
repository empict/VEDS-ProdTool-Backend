package veds.vedsprodtoolbackend.service;

import veds.vedsprodtoolbackend.repo.OfbQueryRepository;
import org.springframework.stereotype.Service;
import java.util.Map;

@Service
public class OfbService {
    private final OfbQueryRepository repo;
    public OfbService(OfbQueryRepository repo){ this.repo = repo; }

    public Map<String,Object> list(String q, int page, int size,
                                   Boolean hasNav, String sort, String dir) {
        return repo.search(q, page, size, hasNav, sort, dir);
    }
}
