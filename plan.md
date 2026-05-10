THIS IS ONLY AND SOLEY HELLO ANDROID APP UPDATES :

You are improving the Hello chat app UI/UX to professional WhatsApp-level quality.

Reference screenshots:
1. Screenshot 2026-05-11 003506.png = Chat theme screen
2. Screenshot 2026-05-11 003519.png = Wallpaper grid
3. Screenshot 2026-05-11 003528.png = Chat color picker
4. Screenshot 2026-05-11 003542.png = Theme preview green
5. Screenshot 2026-05-11 003552.png = Theme preview green variation
6. Screenshot 2026-05-11 003601.png = Theme preview purple
7. Screenshot 2026-05-11 003612.png = Full preview with status/nav

Goal:
Build a professional chat theme/wallpaper system like WhatsApp: simple, clear, preview-first, and not demo-looking.

Implement step by step:

1. Chat Theme Screen
- Create a “Chat theme” settings page.
- Top bar: back button, title, 3-dot menu.
- Show theme cards in horizontal/grid layout.
- Each card must preview:
  - wallpaper background
  - incoming bubble
  - outgoing bubble
  - selected state with white border + check icon
- Add “Customize” section:
  - Chat color
  - Wallpaper
- Text: “The chat color and wallpaper will both change.”

2. Chat Color Screen
- Create circular color picker grid.
- Selected color must show:
  - outer white ring
  - check icon inside circle
- Use dark background.
- Colors should update preview instantly.

3. Wallpaper Screen
- Create wallpaper grid.
- Use rounded rectangular portrait thumbnails.
- 3 columns layout.
- Tapping wallpaper opens preview before applying.
- Do not apply directly without preview.

4. Preview Screen
- Full chat preview page before applying.
- Top bar: back, “Preview”, green check button.
- Show realistic chat background.
- Show sample incoming and outgoing messages.
- Outgoing bubble color must match selected theme color.
- Add bottom page indicator dots.
- Allow swipe left/right between themes/wallpapers.
- Apply only when user taps check.

5. Persist Theme
- Save selected theme per user locally.
- Theme must affect:
  - chat background
  - outgoing message bubble color
  - preview screen
  - current chat room
- It should survive app restart.

6. Fix Image Messages
Current problem: chat images are breaking / forced into wrong fixed size.

Required behavior:
- Image bubble must wrap the whole image naturally.
- Keep original aspect ratio.
- Portrait image should look portrait.
- Landscape image should look landscape.
- Do not crop important content.
- Do not stretch.
- Bubble width should be responsive:
  - max width around 70–78% of screen
  - min width based on image size
- Rounded corners.
- Loading state while image loads.
- Error fallback if image fails.
- Optional caption below image if message has text.

7. File Message Preview
Files must not look like plain broken links.

Create professional file cards for attachments:
- PDF: red/pdf icon + filename + size
- DOC/DOCX: blue document icon
- XLS/XLSX: green spreadsheet icon
- PPT/PPTX: orange presentation icon
- ZIP/RAR: archive icon
- APK: Android/app icon
- Audio: audio icon + duration if available
- Video: thumbnail preview if possible
- Unknown file: generic file icon

Each file bubble should show:
- icon/thumbnail
- filename
- extension
- file size
- download/open button
- upload/download/loading state
- failed state with retry

8. Professional Quality Bar
Do not make toy/demo UI.
Must be production-level:
- consistent spacing
- clean typography
- smooth rounded corners
- dark mode polished
- no oversized bubbles
- no broken glyphs
- no hardcoded fake layout
- reusable components
- responsive for different phone sizes

9. Suggested Component Names
Use or create clean components:
- ChatThemeScreen
- ChatColorScreen
- WallpaperScreen
- ThemePreviewScreen
- ThemeCard
- ColorCircle
- WallpaperThumbnail
- MessageImageBubble
- FileAttachmentBubble
- AttachmentPreviewCard
- useChatTheme / ChatThemeStore

10. Final Validation
After implementation, verify:
- selecting theme opens preview first
- check button applies theme
- restart keeps selected theme
- image messages display portrait/landscape correctly
- PDF/DOC/ZIP/APK files show correct preview cards
- chat UI looks professional, not demo
- no layout breaks on small screens