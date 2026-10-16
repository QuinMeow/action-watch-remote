# Design QA — Action Watch Remote 0.3

final result: passed

## Source and target
- Selected original: amber-instrument.png（单独交付的原始设计参考）
- User refinement: smaller mode headline, primary action 25% taller
- Refined reference: amber-selected-refined.png（单独交付的精修参考） (1088×1440 raster concept, intended 378×496 screen)
- Final 240dpi: docs/ui-v03/final-240/02-connected-home.png (378×496 physical px, 240dpi≈252×331dp)
- Final 320dpi: docs/ui-v03/final-320/02-connected-home.png and 03-connected-bottom.png (378×496 physical px, 320dpi≈189×248dp)
- State: connected, ordinary video, GPS off. Implementation explicitly marks simulated transport; reference is a conceptual real connection. This required label difference is accepted.
- Refined reference and implementation images were opened together in the same comparison input. Concept evaluated at equivalent 378×496 framing; implementation OS status bar is platform-owned and excluded from fidelity demands. Native Android app: CSS/browser checks do not apply; actual Android emulator screenshots provide rendered evidence.
- Full-view evidence: above final files. Focused region evidence: same images opened at original pixel detail; icon, mode and primary-label details are fully readable at 378px, so a separate cropped image is unnecessary.

## Findings
No actionable P0/P1/P2 visual findings remain.

## Required fidelity surfaces
- Typography: 25sp bold mode headline and 20sp primary label preserve requested reduced headline hierarchy. Chinese system fallback clean. Header smaller than concept to preserve native minimum touch target: accepted adaptation. GPS label shortened to single-line GPS关闭/GPS正常 at narrow width.
- Spacing/layout: black base, header→mode→GPS→primary order matches reference. 18dp horizontal margins, 88dp primary button (rounded from 70dp conceptual baseline ×1.25). At 240dpi it renders 132px high and fits one screen; at 320dpi 176px button stays complete in a fixed bottom area while supporting information scrolls above. This intentional density adaptation preserves usable tap targets rather than scaling everything down.
- Colors/tokens: amber main fill #FFCA7A, deep neutral secondary fill, warm white text, black main label match. Flat native fills replacing generated pseudo-gradient are accepted. Simulated connection uses explicit amber text.
- Images/assets: official Google Material Icons VectorDrawable geometry replaces custom-drawn approximations. Crisp settings/location/chevron/record/stop icons; Apache-2.0 license supplied. No raster artwork required. Record icon and label now form one centered group.
- Copy/content: core labels concise, no Debug controls on default normal home; active simulation explicitly marked. GPS on page confirms “模拟GPS · 定位正常”. No unsupported shutter/sleep action added. Necessary permission failure messages remain actionable.

## Comparison history
1. Candidate1 240dpi: record icon sat at far left, text centered independently; custom gear approximated official icon; density verification pending. Blocked until correction.
2. Candidate1 320dpi: GPS已关闭 wrapped and clipped within fixed row; record action only exposed top edge on initial viewport. Blocked P2.
3. Final 240dpi: docs/ui-v03/final-240/02-connected-home.png compared with refined reference. Official icons, grouped record content, mode chevron and single-line GPS fixed first-round issues. Full action visible.
4. Final 320dpi: docs/ui-v03/final-320/02-connected-home.png and 03-connected-bottom.png viewed at original resolution. Fixed bottom record action fully visible, GPS scrolls into complete 48dp single-line row. Earlier responsive P2 resolved. Geometry captured in geometry-top.xml and geometry-bottom.xml.

## Additional states inspected
- final-240/01-home.png: default disconnected home, no simulation entry
- final-240/03-recording.png: timer and stop icon/label, clear recording state
- final-240/04-modes-top.png and 05-modes-bottom.png: mode list and last item reachable
- final-240/07-gps-page-off.png, 08-gps-on.png, 09-gps-off.png: GPS state and simulation disclosure
- Earlier settings/about/Debug screenshots verify hidden-menu path without polluting default home

## Implementation checklist
- [x] Amber selected direction, reduced headline and taller primary action
- [x] Official icons, centered primary icon-label grouping
- [x] 240/320dpi primary action visible, narrow GPS text not clipped
- [x] Simulation visibly distinguished when active, default home clear
- [x] Main visual states reviewed from actual Android emulator captures

## Follow-up polish (P3, non-blocking)
- At 320dpi the top mode prompt approaches the fixed action boundary; a small 4dp separation could further clarify the scrolling region.
- OPPO hardware, actual curve masking, font overrides and physical touch behavior still need real-device testing. Emulator visual pass is not hardware certification or proof of BLE/GPS interoperability.

注：本报告中的设计参考图、截图、录像和运行时证据文件已单独交付；本次公开源码提交不包含这些媒体，文中资产文件名不代表仓库下载路径。
