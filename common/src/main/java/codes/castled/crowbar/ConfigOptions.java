package codes.castled.crowbar;

import java.util.List;
import java.util.function.Predicate;

/**
 * What the CrowBar settings screen shows, independent of any Minecraft version.
 *
 * <p>Each version's screen turns these into vanilla buttons: the label and value become the
 * button text, the tooltip key becomes its tooltip, and a click calls {@link Option#cycle}.
 * Keeping the list here means adding an option is one edit instead of one per jar.
 */
public final class ConfigOptions {
    public enum Tab {
        GENERAL("crowbar.config.tab.general"),
        APPEARANCE("crowbar.config.tab.appearance"),
        CONTROLS("crowbar.config.tab.controls");

        public final String translationKey;

        Tab(String translationKey) {
            this.translationKey = translationKey;
        }
    }

    /**
     * The text after an option's label: either a translation key or a literal string.
     *
     * @param color RGB tint for the value text, or {@code -1} for the default colour
     */
    public record Value(String translationKey, String literal, int color) {
        static Value key(String translationKey) {
            return new Value(translationKey, null, -1);
        }

        static Value color(int rgb) {
            DotColors.Preset preset = DotColors.Preset.of(rgb);
            return preset != null
                    ? new Value(preset.translationKey(), null, rgb)
                    : new Value(null, DotColors.toHex(rgb), rgb);
        }
    }

    public interface Option {
        String labelKey();

        default String tooltipKey() {
            return labelKey() + ".tooltip";
        }

        Value value(CrowBarConfig config);

        /** Whether the button can be clicked; a disabled option still shows its value. */
        default boolean enabled(CrowBarConfig config) {
            return true;
        }

        /** Advances to the next value. The caller saves the config afterwards. */
        void cycle(CrowBarConfig config);
    }

    private ConfigOptions() {
    }

    public static List<Option> options(Tab tab) {
        return switch (tab) {
            case GENERAL -> List.of(
                    feature("crowbar.config.nametags", CrowBarFeature.NAME_TAGS),
                    feature("crowbar.config.skins", CrowBarFeature.SKINS),
                    feature("crowbar.config.show_distance", CrowBarFeature.SHOW_DISTANCE),
                    feature("crowbar.config.view_self", CrowBarFeature.VIEW_SELF),
                    server("heightArrows", toggle("crowbar.config.height_arrows", c -> c.heightArrows, (c, v) -> c.heightArrows = v)),
                    toggle("crowbar.config.server_appearance", c -> c.acceptServerAppearance, (c, v) -> c.acceptServerAppearance = v));
            case APPEARANCE -> List.of(
                    server("labelBackground", toggle("crowbar.config.label_background", c -> c.labelBackground, (c, v) -> c.labelBackground = v)),
                    server("raiseHud", toggle("crowbar.config.raise_hud", c -> c.raiseHud, (c, v) -> c.raiseHud = v)),
                    server("dimUnfocusedLabels", toggle("crowbar.config.dim_labels", c -> c.dimUnfocusedLabels, (c, v) -> c.dimUnfocusedLabels = v)),
                    server("hideOverlappingLabels", toggle("crowbar.config.hide_overlapping", c -> c.hideOverlappingLabels, (c, v) -> c.hideOverlappingLabels = v)),
                    server("skinBorder", toggle("crowbar.config.skin_border", c -> c.skinBorder, (c, v) -> c.skinBorder = v)),
                    server("fadeDistantMarkers", toggle("crowbar.config.fade_markers", c -> c.fadeDistantMarkers, (c, v) -> c.fadeDistantMarkers = v)),
                    server("dotColorMode", new DotColorModeOption()),
                    server("customDotColor", new CustomColorOption()),
                    new SelfColorOption());
            case CONTROLS -> List.of(
                    keyMode("crowbar.config.key.nametags", CrowBarFeature.NAME_TAGS),
                    keyMode("crowbar.config.key.skins", CrowBarFeature.SKINS),
                    keyMode("crowbar.config.key.show_distance", CrowBarFeature.SHOW_DISTANCE),
                    keyMode("crowbar.config.key.view_self", CrowBarFeature.VIEW_SELF));
        };
    }

    /**
     * Greys out an option while the server chooses it, showing the server's value instead of yours.
     * Your own value is kept, and comes back when you leave or turn off Server appearance.
     */
    private static Option server(String field, Option option) {
        return new Option() {
            private boolean locked(CrowBarConfig config) {
                ServerSettings server = ServerConnection.settings();
                return config.acceptServerAppearance && server != null && server.overrides(field);
            }

            @Override
            public String labelKey() {
                return option.labelKey();
            }

            @Override
            public String tooltipKey() {
                return locked(CrowBarConfigStore.config()) ? "crowbar.config.server_locked.tooltip" : option.tooltipKey();
            }

            @Override
            public Value value(CrowBarConfig config) {
                return option.value(ServerSettings.effective(config, ServerConnection.settings()));
            }

            @Override
            public boolean enabled(CrowBarConfig config) {
                // Judged on the effective settings: a server forcing Dot colors to Team also locks
                // Single color, which only applies to Custom.
                return !locked(config) && option.enabled(ServerSettings.effective(config, ServerConnection.settings()));
            }

            @Override
            public void cycle(CrowBarConfig config) {
                if (!locked(config)) option.cycle(config);
            }
        };
    }

    private interface Setter {
        void set(CrowBarConfig config, boolean value);
    }

    private static Option toggle(String labelKey, Predicate<CrowBarConfig> getter, Setter setter) {
        return new Option() {
            @Override
            public String labelKey() {
                return labelKey;
            }

            @Override
            public Value value(CrowBarConfig config) {
                return onOff(getter.test(config));
            }

            @Override
            public void cycle(CrowBarConfig config) {
                setter.set(config, !getter.test(config));
            }
        };
    }

    /** A feature switch. While its key is set to hold, the key decides and the button is locked. */
    private static Option feature(String labelKey, CrowBarFeature feature) {
        return new Option() {
            @Override
            public String labelKey() {
                return labelKey;
            }

            @Override
            public Value value(CrowBarConfig config) {
                return feature.keyMode(config) == KeyMode.HOLD
                        ? Value.key("crowbar.config.while_held")
                        : onOff(feature.isOn(config));
            }

            @Override
            public boolean enabled(CrowBarConfig config) {
                return feature.keyMode(config) == KeyMode.TOGGLE;
            }

            @Override
            public void cycle(CrowBarConfig config) {
                feature.set(config, !feature.isOn(config));
            }
        };
    }

    private static Option keyMode(String labelKey, CrowBarFeature feature) {
        return new Option() {
            @Override
            public String labelKey() {
                return labelKey;
            }

            @Override
            public String tooltipKey() {
                return "crowbar.config.key_mode.tooltip";
            }

            @Override
            public Value value(CrowBarConfig config) {
                return Value.key(feature.keyMode(config) == KeyMode.HOLD
                        ? "crowbar.config.key_mode.hold"
                        : "crowbar.config.key_mode.toggle");
            }

            @Override
            public void cycle(CrowBarConfig config) {
                feature.setKeyMode(config, feature.keyMode(config).next());
            }
        };
    }

    private static final class DotColorModeOption implements Option {
        @Override
        public String labelKey() {
            return "crowbar.config.dot_colors";
        }

        @Override
        public Value value(CrowBarConfig config) {
            return Value.key(switch (config.dotColorMode) {
                case TEAM -> "crowbar.config.dot_colors.team";
                case UNIQUE -> "crowbar.config.dot_colors.unique";
                case CUSTOM -> "crowbar.config.dot_colors.custom";
            });
        }

        @Override
        public void cycle(CrowBarConfig config) {
            config.dotColorMode = config.dotColorMode.next();
        }
    }

    private static final class CustomColorOption implements Option {
        @Override
        public String labelKey() {
            return "crowbar.config.custom_color";
        }

        @Override
        public Value value(CrowBarConfig config) {
            return Value.color(DotColors.parseHex(config.customDotColor, 0xFFFFFF));
        }

        @Override
        public boolean enabled(CrowBarConfig config) {
            return config.dotColorMode == DotColorMode.CUSTOM;
        }

        @Override
        public void cycle(CrowBarConfig config) {
            config.customDotColor = DotColors.toHex(nextPreset(DotColors.parseHex(config.customDotColor, 0xFFFFFF)).rgb);
        }
    }

    private static final class SelfColorOption implements Option {
        @Override
        public String labelKey() {
            return "crowbar.config.self_color";
        }

        /** On a server that shares chosen colours, say that others will see it too. */
        @Override
        public String tooltipKey() {
            ServerSettings server = ServerConnection.settings();
            return server != null && server.acceptsPlayerColor()
                    ? "crowbar.config.self_color.tooltip.server"
                    : "crowbar.config.self_color.tooltip";
        }

        @Override
        public Value value(CrowBarConfig config) {
            Integer rgb = CrowBarConfig.AUTO_COLOR.equals(config.selfDotColor)
                    ? null
                    : DotColors.parseHex(config.selfDotColor);
            return rgb == null ? Value.key("crowbar.config.self_color.auto") : Value.color(rgb);
        }

        /** Automatic, then every preset, then back to automatic. */
        @Override
        public void cycle(CrowBarConfig config) {
            Integer rgb = CrowBarConfig.AUTO_COLOR.equals(config.selfDotColor)
                    ? null
                    : DotColors.parseHex(config.selfDotColor);
            if (rgb == null) {
                config.selfDotColor = DotColors.toHex(DotColors.Preset.values()[0].rgb);
                return;
            }
            DotColors.Preset current = DotColors.Preset.of(rgb);
            DotColors.Preset[] presets = DotColors.Preset.values();
            config.selfDotColor = current != null && current.ordinal() == presets.length - 1
                    ? CrowBarConfig.AUTO_COLOR
                    : DotColors.toHex(nextPreset(rgb).rgb);
        }
    }

    /** The preset after {@code rgb}; a colour typed into the file by hand cycles to the first. */
    private static DotColors.Preset nextPreset(int rgb) {
        DotColors.Preset current = DotColors.Preset.of(rgb);
        DotColors.Preset[] presets = DotColors.Preset.values();
        return current == null ? presets[0] : presets[(current.ordinal() + 1) % presets.length];
    }

    private static Value onOff(boolean on) {
        return Value.key(on ? "crowbar.config.on" : "crowbar.config.off");
    }
}
