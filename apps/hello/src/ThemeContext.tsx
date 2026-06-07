import { createContext, useContext, useEffect, useState, ReactNode } from 'react';

type Theme = 'light' | 'dark' | 'system' | 'cute';

interface ThemeContextType {
  theme: Theme;
  setTheme: (theme: Theme) => void;
  isDark: boolean;
  enterIsSend: boolean;
  setEnterIsSend: (val: boolean) => void;
  chatWallpaper: string;
  setChatWallpaper: (val: string) => void;
  chatWallpaperOpacity: number;
  setChatWallpaperOpacity: (val: number) => void;
}

const ThemeContext = createContext<ThemeContextType>({ 
  theme: 'cute',
  setTheme: () => {}, 
  isDark: false,
  enterIsSend: true,
  setEnterIsSend: () => {},
  chatWallpaper: 'cute-theme',
  setChatWallpaper: () => {},
  chatWallpaperOpacity: 100,
  setChatWallpaperOpacity: () => {}
});

export const useTheme = () => useContext(ThemeContext);

const CUTE_THEME_MIGRATION_KEY = 'whatsclone_cute_theme_migrated';

export const ThemeProvider = ({ children }: { children: ReactNode }) => {
  const [theme, setTheme] = useState<Theme>(() => {
     const saved = localStorage.getItem('whatsclone_theme') as Theme | null;
     if (!localStorage.getItem(CUTE_THEME_MIGRATION_KEY) && (!saved || saved === 'system')) {
       localStorage.setItem(CUTE_THEME_MIGRATION_KEY, 'true');
       localStorage.setItem('whatsclone_theme', 'cute');
       return 'cute';
     }
     return saved || 'cute';
  });
  const [enterIsSend, setEnterIsSend] = useState<boolean>(() => {
    const val = localStorage.getItem('whatsclone_enter_send');
    return val !== null ? val === 'true' : true;
  });
  const [chatWallpaper, setChatWallpaper] = useState<string>(() => {
    const saved = localStorage.getItem('whatsclone_wallpaper');
    if (!localStorage.getItem(`${CUTE_THEME_MIGRATION_KEY}_wallpaper`) && (!saved || saved === 'default')) {
      localStorage.setItem(`${CUTE_THEME_MIGRATION_KEY}_wallpaper`, 'true');
      localStorage.setItem('whatsclone_wallpaper', 'cute-theme');
      return 'cute-theme';
    }
    return saved || 'cute-theme';
  });
  const [chatWallpaperOpacity, setChatWallpaperOpacity] = useState<number>(() => {
    const val = localStorage.getItem('whatsclone_wallpaper_opacity');
    return val !== null ? parseInt(val, 10) : 100;
  });
  const [isDark, setIsDark] = useState(false);

  useEffect(() => {
    localStorage.setItem('whatsclone_theme', theme);
    localStorage.setItem('whatsclone_enter_send', enterIsSend.toString());
    localStorage.setItem('whatsclone_wallpaper', chatWallpaper);
    localStorage.setItem('whatsclone_wallpaper_opacity', chatWallpaperOpacity.toString());
    const root = window.document.documentElement;
    
    const updateTheme = () => {
      const isSystemDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
      const willBeCute = theme === 'cute';
      const willBeDark = theme === 'dark' || (theme === 'system' && isSystemDark);
      
      root.classList.remove('dark', 'light', 'cute');
      if (willBeCute) {
        root.classList.add('cute');
        document.body.style.backgroundColor = '#fff1f7';
        document.body.style.color = '#84234b';
      } else if (willBeDark) {
        root.classList.add('dark');
        document.body.style.backgroundColor = '#0f172a'; // slate-900
        document.body.style.color = '#f8fafc'; // slate-50
      } else {
        root.classList.add('light');
        document.body.style.backgroundColor = '#f8fafc'; // slate-50
        document.body.style.color = '#1e293b'; // slate-800
      }
      setIsDark(willBeDark);
    };

    updateTheme();

    const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)');
    const handleChange = () => {
      if (theme === 'system') updateTheme();
    };
    mediaQuery.addEventListener('change', handleChange);
    return () => mediaQuery.removeEventListener('change', handleChange);
  }, [theme, enterIsSend, chatWallpaper, chatWallpaperOpacity]);

  return (
    <ThemeContext.Provider value={{ theme, setTheme, isDark, enterIsSend, setEnterIsSend, chatWallpaper, setChatWallpaper, chatWallpaperOpacity, setChatWallpaperOpacity }}>
      {children}
    </ThemeContext.Provider>
  );
};
