package web.learning.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class LibraryServicePoetryTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void userCanBrowseAndFilterLocalPoetryByDynastyAndAuthor() throws Exception {
        Path datasets = temporaryFolder.newFolder("datasets").toPath();
        Path resources = temporaryFolder.newFolder("resources").toPath();
        String[] poems = {
                "{\"title\":\"静夜思\",\"author\":\"李白\",\"dynasty\":\"唐\",\"paragraphs\":[\"床前明月光\"]}",
                "{\"title\":\"春夜喜雨\",\"author\":\"杜甫\",\"dynasty\":\"唐\",\"paragraphs\":[\"好雨知时节\"]}",
                "{\"title\":\"水调歌头\",\"author\":\"苏轼\",\"dynasty\":\"宋\",\"paragraphs\":[\"明月几时有\"]}"
        };
        Files.write(datasets.resolve("poetry.jsonl"), String.join("\n", poems).concat("\n").getBytes(StandardCharsets.UTF_8));
        Path index = Files.createDirectories(datasets.resolve("poetry-idx"));
        StringBuilder catalog = new StringBuilder();
        long offset = 0;
        for (String poem : poems) {
            byte[] body = poem.getBytes(StandardCharsets.UTF_8);
            ObjectMapper mapper = new ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode node = mapper.readTree(poem);
            catalog.append(offset).append('\t').append(body.length).append('\t')
                    .append(node.path("dynasty").asText()).append('\t')
                    .append(node.path("author").asText()).append('\t')
                    .append(node.path("title").asText()).append('\n');
            offset += body.length + 1;
        }
        Files.write(index.resolve("all.tsv"), catalog.toString().getBytes(StandardCharsets.UTF_8));

        LibraryService service = new LibraryService(datasets.toString(), resources.toString(), new ObjectMapper());
        Map<String, Object> firstPage = service.poetryPage("", "", "", 1, 2);
        assertEquals(3L, ((Number) firstPage.get("total")).longValue());
        assertEquals(2, ((List<?>) firstPage.get("items")).size());

        Map<String, Object> filtered = service.poetryPage("", "唐", "李白", 1, 20);
        assertEquals(1L, ((Number) filtered.get("total")).longValue());
        Map<?, ?> poem = (Map<?, ?>) ((List<?>) filtered.get("items")).get(0);
        assertEquals("静夜思", poem.get("title"));
        assertEquals("唐", poem.get("dynasty"));
    }

    @Test
    public void userBrowsesUsingPagedCatalogWhenInstalled() throws Exception {
        Path datasets = temporaryFolder.newFolder("catalog-datasets").toPath();
        Path resources = temporaryFolder.newFolder("catalog-resources").toPath();
        String first = "{\"title\":\"静夜思\",\"author\":\"李白\",\"dynasty\":\"唐\",\"paragraphs\":[\"床前明月光\"]}";
        String hidden = "{\"title\":\"未收录\",\"author\":\"佚名\",\"dynasty\":\"其他\",\"paragraphs\":[\"不应出现\"]}";
        Files.write(datasets.resolve("poetry.jsonl"), (first + "\n" + hidden + "\n").getBytes(StandardCharsets.UTF_8));
        Path index = Files.createDirectories(datasets.resolve("poetry-idx"));
        String catalog = "0\t" + first.getBytes(StandardCharsets.UTF_8).length + "\t唐\t李白\t静夜思\n";
        Files.write(index.resolve("all.tsv"), catalog.getBytes(StandardCharsets.UTF_8));

        LibraryService service = new LibraryService(datasets.toString(), resources.toString(), new ObjectMapper());
        Map<String, Object> page = service.poetryPage("", "", "", 1, 20);
        assertEquals(1L, ((Number) page.get("total")).longValue());
        assertEquals("静夜思", ((Map<?, ?>) ((List<?>) page.get("items")).get(0)).get("title"));
    }
}
