
package br.com.reaproveita.service;

import br.com.reaproveita.dto.AiFoodAnalysisResponse;

public interface AiModelClient {

    AiFoodAnalysisResponse analyze(String description);

}
