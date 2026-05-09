/**
 * Hello Design System Tokens
 * Shared design system for web & Android (values only, no implementation)
 */

export const tokens = {
  // Colors - Light Theme
  light: {
    // Background
    bg: '#f3efe7',
    bgStrong: '#fbf8f2',
    bgMuted: '#f0ebe5',
    
    // Surfaces (glass/panels)
    panel: 'rgba(255, 255, 255, 0.78)',
    panelStrong: 'rgba(255, 255, 255, 0.94)',
    panelMuted: 'rgba(255, 255, 255, 0.62)',
    panelGlass: 'rgba(255, 255, 255, 0.85)',
    
    // Text
    text: '#172033',
    textMuted: '#5d6a82',
    textSubtle: '#a0aab5',
    
    // Borders
    border: 'rgba(15, 23, 42, 0.08)',
    borderStrong: 'rgba(15, 23, 42, 0.14)',
    
    // Accent (green)
    accent: '#0f8f78',
    accentStrong: '#0a6e5d',
    accentSoft: 'rgba(15, 143, 120, 0.12)',
    accentLight: 'rgba(15, 143, 120, 0.08)',
    
    // Status
    danger: '#cf4d4d',
    dangerSoft: 'rgba(207, 77, 77, 0.1)',
    warning: '#b77800',
    warningSoft: 'rgba(183, 120, 0, 0.1)',
    success: '#0f8f78',
    successSoft: 'rgba(15, 143, 120, 0.1)',
  },

  // Colors - Dark Theme
  dark: {
    // Background
    bg: '#071219',
    bgStrong: '#0d1821',
    bgMuted: '#0a1420',
    
    // Surfaces (glass/panels)
    panel: 'rgba(15, 26, 33, 0.8)',
    panelStrong: 'rgba(17, 27, 33, 0.94)',
    panelMuted: 'rgba(13, 24, 33, 0.7)',
    panelGlass: 'rgba(20, 30, 38, 0.75)',
    
    // Text
    text: '#edf4fb',
    textMuted: '#9aa9bd',
    textSubtle: '#6d7c8e',
    
    // Borders
    border: 'rgba(226, 232, 240, 0.08)',
    borderStrong: 'rgba(226, 232, 240, 0.14)',
    
    // Accent (green)
    accent: '#28c0a4',
    accentStrong: '#49d3bc',
    accentSoft: 'rgba(40, 192, 164, 0.16)',
    accentLight: 'rgba(40, 192, 164, 0.1)',
    
    // Status
    danger: '#ff7b84',
    dangerSoft: 'rgba(255, 123, 132, 0.15)',
    warning: '#ffcb66',
    warningSoft: 'rgba(255, 203, 102, 0.15)',
    success: '#28c0a4',
    successSoft: 'rgba(40, 192, 164, 0.15)',
  },

  // Typography
  typography: {
    fontFamily: '"Manrope", sans-serif',
    sizes: {
      xs: '12px',
      sm: '13px',
      base: '14px',
      lg: '16px',
      xl: '18px',
      '2xl': '20px',
      '3xl': '24px',
      '4xl': '28px',
    },
    weights: {
      regular: 400,
      medium: 500,
      semibold: 600,
      bold: 700,
      extrabold: 800,
    },
    lineHeight: {
      tight: 1.2,
      normal: 1.4,
      relaxed: 1.6,
    },
  },

  // Spacing
  spacing: {
    0: '0',
    1: '2px',
    2: '4px',
    3: '6px',
    4: '8px',
    5: '10px',
    6: '12px',
    8: '16px',
    10: '20px',
    12: '24px',
    16: '32px',
    20: '40px',
    24: '48px',
    32: '64px',
  },

  // Border Radius
  radius: {
    none: '0',
    sm: '8px',
    md: '12px',
    lg: '16px',
    xl: '20px',
    '2xl': '28px',
    full: '9999px',
  },

  // Shadows
  shadow: {
    none: 'none',
    xs: '0 2px 4px rgba(0, 0, 0, 0.04)',
    sm: '0 4px 6px rgba(0, 0, 0, 0.05)',
    md: '0 10px 28px rgba(18, 28, 45, 0.06)',
    lg: '0 18px 44px rgba(18, 28, 45, 0.08)',
    xl: '0 24px 56px rgba(18, 28, 45, 0.1)',
    darkMd: '0 10px 28px rgba(0, 0, 0, 0.24)',
    darkLg: '0 18px 44px rgba(0, 0, 0, 0.28)',
    darkXl: '0 24px 56px rgba(0, 0, 0, 0.34)',
  },

  // Animations / Motion
  animation: {
    // Durations (ms)
    duration: {
      instant: 0,
      fast: 120,
      base: 200,
      slow: 280,
      slower: 400,
      slowest: 600,
    },

    // Easing functions
    easing: {
      linear: 'linear',
      easeIn: 'cubic-bezier(0.4, 0, 1, 1)',
      easeOut: 'cubic-bezier(0, 0, 0.2, 1)',
      easeInOut: 'cubic-bezier(0.4, 0, 0.2, 1)',
      spring: 'cubic-bezier(0.175, 0.885, 0.32, 1.275)',
      bounceIn: 'cubic-bezier(0.68, -0.55, 0.265, 1.55)',
      bounceOut: 'cubic-bezier(0.68, -0.55, 0.265, 1.55)',
    },

    // Spring presets (for Framer Motion)
    spring: {
      smooth: { type: 'spring', stiffness: 300, damping: 30, mass: 1 },
      bouncy: { type: 'spring', stiffness: 500, damping: 20, mass: 0.8 },
      elastic: { type: 'spring', stiffness: 400, damping: 15, mass: 0.8 },
      subtle: { type: 'spring', stiffness: 200, damping: 50, mass: 1 },
    },
  },

  // Z-index scale
  zIndex: {
    hide: -1,
    base: 0,
    dropdown: 10,
    sticky: 20,
    fixed: 30,
    backdrop: 40,
    modal: 50,
    tooltip: 60,
    notification: 70,
    popover: 80,
  },

  // Breakpoints
  breakpoint: {
    xs: '320px',
    sm: '480px',
    md: '768px',
    lg: '1024px',
    xl: '1280px',
    '2xl': '1536px',
  },

  // Chat-specific
  chat: {
    bubbleRadius: '20px',
    inputPadding: '16px',
    avatarSize: '40px',
    messageGap: '8px',
    scrollbarWidth: '6px',
  },
};

export type ColorKey = keyof typeof tokens.light;
export type TypographyKey = keyof typeof tokens.typography;
export type SpacingKey = keyof typeof tokens.spacing;
export type RadiusKey = keyof typeof tokens.radius;
export type ShadowKey = keyof typeof tokens.shadow;
export type AnimationKey = keyof typeof tokens.animation;
