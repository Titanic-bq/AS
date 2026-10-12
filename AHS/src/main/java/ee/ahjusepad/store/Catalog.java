package ee.ahjusepad.store;

import java.util.List;
import java.util.Locale;

/**
 * Poe toodete kataloog, mida hoitakse rakenduse töömälus.
 * Uue toote lisamiseks lisa loendisse Product-kirje koos nime, kategooria,
 * tehnilise lühikirjelduse, hinna, pildi veebiaadressi, märgise ja tutvustusega.
 * Kategooria nimi peab ühtima veebilehe filtrite ja päringuvormi valikutega.
 */
public final class Catalog {
    private Catalog() {}

    // Iga kirje andmeid kasutatakse veebilehe tootekaardi koostamisel.
    private static final List<Product> PRODUCTS = List.of(
            new Product("Nord 85 Panorama", "Fireplaces", "Wood · 9.2 kW", "From €3,490",
                    "https://images.unsplash.com/photo-1614255976202-8ce52bfcb655?auto=format&fit=crop&w=1200&q=85",
                    "Bestseller", "A panoramic wood-burning fireplace for contemporary living spaces."),
            new Product("Lacunza Atlantic 603", "Stoves", "Wood · 10.5 kW", "From €2,180",
                    "https://images.unsplash.com/photo-1723748972084-4124765e0a55?auto=format&fit=crop&w=1200&q=85",
                    "New", "A considered, high-output stove with timeless proportions."),
            new Product("Forma 95 Wood", "Fireplace inserts", "Wood · 11.6 kW", "From €3,890",
                    "https://images.unsplash.com/photo-1603039531759-1a1bbe4f9f94?auto=format&fit=crop&w=1000&q=85",
                    "", "A clean-lined insert designed to give an existing fireplace a new life."),
            new Product("Uunisepät Lumi", "Ovens", "Storage · 18 h warmth", "From €5,940",
                    "https://images.unsplash.com/photo-1728404259075-209cfb5bb89c?auto=format&fit=crop&w=1000&q=85",
                    "Efficient", "A heat-storing oven that continues to radiate warmth long after firing."),
            new Product("Nordic Compact", "Ready-made fireplaces", "Wood · compact format", "Ask for price",
                    "https://images.unsplash.com/photo-1628744876497-eb30460be9f6?auto=format&fit=crop&w=1200&q=85",
                    "", "A balanced ready-made solution for smaller rooms and renovations."),
            new Product("Installation Essentials", "Accessories", "Flue · hearth · fittings", "Ask for price",
                    "https://images.unsplash.com/photo-1512916194211-3f2b7f5f7de3?auto=format&fit=crop&w=1200&q=85",
                    "", "The components and finishing details that help complete a safe installation.")
    );

    public static List<Product> all() {
        return PRODUCTS;
    }

    /**
     * Tagastab tooted, mis vastavad kategooriafiltrile ja otsingusõnale.
     * Kategooria võrdlus ei arvesta suur- ega väiketähti; otsing hõlmab
     * toote nime, kategooriat, tehnilisi andmeid ja tutvustust.
     */
    public static List<Product> search(String type, String query) {
        String normalizedType = type == null ? "All" : type.strip();
        String normalizedQuery = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        return PRODUCTS.stream()
                .filter(p -> normalizedType.isBlank() || normalizedType.equalsIgnoreCase("All")
                        || p.type().equalsIgnoreCase(normalizedType))
                .filter(p -> normalizedQuery.isBlank() || String.join(" ", p.name(), p.type(), p.detail(),
                                p.description()).toLowerCase(Locale.ROOT).contains(normalizedQuery))
                .toList();
    }
}
