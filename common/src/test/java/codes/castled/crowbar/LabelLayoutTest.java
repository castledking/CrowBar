package codes.castled.crowbar;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LabelLayoutTest {
    private record Label(String name, int left, int right, int distanceFromCenter) {
    }

    private static List<String> visible(Label... labels) {
        return LabelLayout.withoutOverlaps(List.of(labels), Label::left, Label::right, Label::distanceFromCenter)
                .stream().map(Label::name).toList();
    }

    @Test
    void labelNearestTheCrosshairWinsAnOverlap() {
        assertEquals(List.of("Alex"), visible(
                new Label("Steve", 100, 140, 30),
                new Label("Alex", 120, 150, 5)));
    }

    @Test
    void separatedLabelsAllSurviveInTheirOriginalOrder() {
        assertEquals(List.of("Steve", "Alex"), visible(
                new Label("Steve", 10, 40, 80),
                new Label("Alex", 100, 130, 5)));
    }

    @Test
    void keepsAGapBetweenNeighbours() {
        assertEquals(List.of("Alex"), visible(
                new Label("Steve", 100, 119, 30),
                new Label("Alex", 120, 150, 5)));
        assertEquals(List.of("Steve", "Alex"), visible(
                new Label("Steve", 100, 118, 30),
                new Label("Alex", 120, 150, 5)));
    }

    @Test
    void aHiddenLabelDoesNotBlockOthers() {
        // Herobrine loses to Alex, so it must not also knock out Steve.
        assertEquals(List.of("Steve", "Alex"), visible(
                new Label("Steve", 60, 95, 40),
                new Label("Herobrine", 90, 125, 20),
                new Label("Alex", 110, 150, 5)));
    }
}
