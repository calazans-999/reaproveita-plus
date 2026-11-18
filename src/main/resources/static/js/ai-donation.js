
document.addEventListener("DOMContentLoaded", () => {

    const form = document.querySelector(
        'form[action$="/donations"]'
    );

    if (!form) {
        return;
    }

    const titleInput = form.querySelector(
        'input[name="title"]'
    );

    const categoryInput = form.querySelector(
        'select[name="category"]'
    );

    const quantityInput = form.querySelector(
        'input[name="quantityKg"]'
    );

    const storageInput = form.querySelector(
        'input[name="storageCondition"]'
    );

    const csrfInput = form.querySelector(
        'input[name="_csrf"]'
    );

    const submitButton = form.querySelector(
        'button[type="submit"]'
    );

    if (
        !titleInput ||
        !categoryInput ||
        !quantityInput ||
        !storageInput ||
        !csrfInput ||
        !submitButton
    ) {
        return;
    }



    const analyzeButton = document.createElement("button");

    analyzeButton.type = "button";
    analyzeButton.className = "btn-small";
    analyzeButton.textContent = "Analisar histórico";



    const resultBox = document.createElement("section");

    resultBox.hidden = true;
    resultBox.setAttribute("role", "status");
    resultBox.setAttribute("aria-live", "polite");

    Object.assign(resultBox.style, {
        marginTop: "16px",
        marginBottom: "16px",
        padding: "18px",
        border: "1px solid #dce8df",
        borderRadius: "14px",
        backgroundColor: "#f8fbf9",
        color: "#1d3027"
    });

    submitButton.before(analyzeButton);
    analyzeButton.after(resultBox);


    function element(tag, text, styles = {}) {

        const node = document.createElement(tag);

        if (text !== undefined && text !== null) {
            node.textContent = String(text);
        }

        Object.assign(node.style, styles);

        return node;
    }

    function clearResult() {

        resultBox.replaceChildren();
        resultBox.hidden = false;

        resultBox.style.borderColor = "#dce8df";
        resultBox.style.backgroundColor = "#f8fbf9";
    }

    function showMessage(message, isError = false) {

        clearResult();

        if (isError) {
            resultBox.style.borderColor = "#efb2b2";
            resultBox.style.backgroundColor = "#fff5f5";
        }

        resultBox.appendChild(
            element("p", message, {
                margin: "0",
                fontSize: "14px",
                lineHeight: "1.6"
            })
        );
    }



    function createInfoCard(label, value) {

        const card = element("div", null, {
            padding: "12px",
            border: "1px solid #e0e9e2",
            borderRadius: "10px",
            backgroundColor: "#ffffff",
            minWidth: "0"
        });

        card.appendChild(
            element("div", label, {
                fontSize: "12px",
                color: "#597063",
                marginBottom: "6px"
            })
        );

        card.appendChild(
            element("strong", value, {
                display: "block",
                fontSize: "18px",
                overflowWrap: "anywhere"
            })
        );

        return card;
    }


    function createWarning(message) {

        const warning = element("div", null, {
            padding: "12px",
            borderRadius: "10px",
            backgroundColor: "#fff8e8",
            border: "1px solid #efdca7",
            color: "#6e541e",
            fontSize: "13px",
            lineHeight: "1.6"
        });

        warning.textContent = message;

        return warning;
    }


    function extractTerms(explanation) {

        if (!explanation) {
            return [];
        }

        const match = explanation.match(
            /Termos encontrados:\s*([^.]*)/i
        );

        if (!match || !match[1]) {
            return [];
        }

        return match[1]
            .split(",")
            .map(term => term.trim())
            .filter(Boolean)
            .slice(0, 12);
    }


    function isAmbiguous(suggestion) {

        const explanation = (
            suggestion.explanation || ""
        ).toLowerCase();

        return (
            explanation.includes("ambigua") ||
            explanation.includes("ambígua") ||
            explanation.includes("nenhuma doacao semelhante") ||
            explanation.includes("nenhuma doação semelhante")
        );
    }


    function renderSuggestion(suggestion) {

        clearResult();

        const ambiguous = isAmbiguous(suggestion);

        const explanation = suggestion.explanation ||
            "Sem explicação adicional.";


        const heading = element(
            "h3",
            "Análise do histórico",
            {
                margin: "0 0 14px",
                fontSize: "17px"
            }
        );

        resultBox.appendChild(heading);

        if (ambiguous) {

            resultBox.appendChild(
                createWarning(
                    "Não foi possível determinar uma categoria " +
                    "com segurança a partir do histórico. " +
                    "Revise a descrição e escolha a categoria " +
                    "manualmente."
                )
            );
        }


        const grid = element("div", null, {
            display: "grid",
            gridTemplateColumns:
                "repeat(auto-fit, minmax(150px, 1fr))",
            gap: "10px",
            marginTop: "12px",
            marginBottom: "14px"
        });


        const category = ambiguous
            ? "Classificação inconclusiva"
            : (suggestion.category || "OTHER");

        const quantity = suggestion.quantityKg != null
            ? suggestion.quantityKg + " kg"
            : "Não identificada";

        grid.appendChild(
            createInfoCard(
                ambiguous
                    ? "Resultado da classificação"
                    : "Categoria sugerida",
                category
            )
        );

        grid.appendChild(
            createInfoCard(
                "Quantidade identificada",
                quantity
            )
        );

        resultBox.appendChild(grid);

        const terms = extractTerms(explanation);

        if (terms.length > 0) {

            resultBox.appendChild(
                element("strong", "Termos encontrados", {
                    display: "block",
                    fontSize: "13px",
                    marginBottom: "8px"
                })
            );

            const termsContainer = element("div", null, {
                display: "flex",
                flexWrap: "wrap",
                gap: "6px",
                marginBottom: "14px"
            });

            terms.forEach(term => {

                const tag = element("span", term, {
                    display: "inline-block",
                    padding: "5px 9px",
                    borderRadius: "20px",
                    backgroundColor: "#e5f3ea",
                    border: "1px solid #d0e6d7",
                    color: "#17653f",
                    fontSize: "12px"
                });

                termsContainer.appendChild(tag);
            });

            resultBox.appendChild(termsContainer);
        }

        resultBox.appendChild(
            element(
                "strong",
                "Como chegamos a esta sugestão",
                {
                    display: "block",
                    fontSize: "13px",
                    marginBottom: "6px"
                }
            )
        );

        resultBox.appendChild(
            element("p", explanation, {
                fontSize: "13px",
                lineHeight: "1.6",
                color: "#52665a",
                margin: "0 0 14px"
            })
        );


        if (
            typeof suggestion.storageCondition === "string" &&
            suggestion.storageCondition.trim()
        ) {

            resultBox.appendChild(
                element(
                    "p",
                    "Conservação sugerida: " +
                    suggestion.storageCondition,
                    {
                        fontSize: "13px",
                        lineHeight: "1.6"
                    }
                )
            );
        }

        resultBox.appendChild(
            createWarning(
                "Esta análise é uma sugestão baseada em " +
                "doações anteriores. A categoria, a quantidade, " +
                "as condições de conservação, a validade e " +
                "os requisitos sanitários devem ser conferidos " +
                "antes da publicação."
            )
        );


        const acceptButton = document.createElement("button");

        acceptButton.type = "button";
        acceptButton.className = "btn-small";

        acceptButton.textContent = ambiguous
            ? "Aplicar somente quantidade"
            : "Aplicar sugestões";

        acceptButton.style.marginTop = "14px";

        // Não oferecer ação sem quantidade
        // quando a classificação é inconclusiva.
        if (
            ambiguous &&
            (
                suggestion.quantityKg == null ||
                !Number.isFinite(Number(suggestion.quantityKg)) ||
                Number(suggestion.quantityKg) <= 0
            )
        ) {
            acceptButton.disabled = true;
            acceptButton.textContent =
                "Quantidade não identificada";
        }

        resultBox.appendChild(acceptButton);


        acceptButton.addEventListener("click", () => {

            // Não aplicar categoria inconclusiva.
            if (!ambiguous) {

                const categoryOption = Array.from(
                    categoryInput.options
                ).find(option =>
                    option.value === suggestion.category
                );

                if (categoryOption) {

                    categoryInput.value = suggestion.category;

                    categoryInput.dispatchEvent(
                        new Event("change", {
                            bubbles: true
                        })
                    );
                }
            }

            // Aplicar quantidade somente quando válida.
            const parsedQuantity = Number(
                suggestion.quantityKg
            );

            if (
                suggestion.quantityKg != null &&
                Number.isFinite(parsedQuantity) &&
                parsedQuantity > 0
            ) {

                quantityInput.value = String(parsedQuantity);

                quantityInput.dispatchEvent(
                    new Event("input", {
                        bubbles: true
                    })
                );
            }

            // Aplicar conservação apenas quando fornecida
            // e quando a classificação não for ambígua.
            if (
                !ambiguous &&
                typeof suggestion.storageCondition === "string" &&
                suggestion.storageCondition.trim()
            ) {

                storageInput.value =
                    suggestion.storageCondition.slice(0, 255);

                storageInput.dispatchEvent(
                    new Event("input", {
                        bubbles: true
                    })
                );
            }

            // Confirmação após aplicação.
            showMessage(
                ambiguous
                    ? "A quantidade identificada foi aplicada. " +
                      "Escolha a categoria manualmente e " +
                      "confira todos os campos."
                    : "Sugestões aplicadas. Confira os campos, " +
                      "informe a validade, verifique a conservação " +
                      "e confirme a declaração sanitária antes " +
                      "de publicar."
            );
        });
    }


    analyzeButton.addEventListener("click", async () => {

        const description = titleInput.value.trim();


        if (
            description.length < 5 ||
            description.length > 1000
        ) {

            showMessage(
                "A descrição deve conter entre 5 e " +
                "1000 caracteres.",
                true
            );

            titleInput.focus();

            return;
        }


        analyzeButton.disabled = true;
        analyzeButton.textContent = "Analisando...";

        showMessage(
            "Consultando o histórico de doações..."
        );

        try {


            const response = await fetch(
                "/donations/ai/analyze",
                {
                    method: "POST",
                    credentials: "same-origin",
                    headers: {
                        "Content-Type": "application/json",
                        "X-CSRF-TOKEN": csrfInput.value
                    },
                    body: JSON.stringify({
                        description
                    })
                }
            );


            if (!response.ok) {

                if (response.status === 503) {

                    throw new Error(
                        "A análise histórica está " +
                        "indisponível no momento."
                    );
                }

                if (
                    response.status === 403 ||
                    response.status === 401
                ) {

                    throw new Error(
                        "Acesso negado ou sessão expirada. " +
                        "Atualize a página e tente novamente."
                    );
                }

                if (response.status === 400) {

                    throw new Error(
                        "Descrição inválida. " +
                        "Revise o texto informado."
                    );
                }

                throw new Error(
                    "Não foi possível concluir a análise."
                );
            }


            const suggestion = await response.json();

            if (
                !suggestion ||
                !suggestion.category ||
                suggestion.requiresReview !== true
            ) {

                throw new Error(
                    "O histórico retornou uma sugestão inválida."
                );
            }


            renderSuggestion(suggestion);

        } catch (error) {

            showMessage(
                error.message ||
                "Erro inesperado durante a análise.",
                true
            );

        } finally {

            analyzeButton.disabled = false;
            analyzeButton.textContent = "Analisar histórico";
        }
    });

});
