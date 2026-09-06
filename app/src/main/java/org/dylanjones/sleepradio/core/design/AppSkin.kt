package org.dylanjones.sleepradio.core.design

/**
 * The two selectable visual skins. See RETROSYNC_PLAN.md section 3.
 *
 * Each skin is a full `PlayerScreen` implementation (not just a recolour) built
 * on shared ViewModels. The `AppSkin` token interface and the skin-specific
 * screens land in Phase 2.
 */
enum class SkinId { NEON, INDUSTRIAL }
