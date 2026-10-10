# Task Assignment: Explorer Survey 1 (Video Engine)

## Objective
Survey GoBeats codebase focusing on Video Engine & Media Playback architecture for R1.

## Scope & Guidelines
- Read `c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\ORIGINAL_REQUEST.md`.
- Investigate existing video playback code in GoBeats: ExoPlayer / Media3 usage, video player UI, current audio handling, LoudnessEnhancer or audio processor hook points, subtitle rendering (current state, track selection, format support), video quality selection handling (track selector constraints causing black-screen on local single rendition), video resume / playback position storage (existing Room/DataStore schemas), floating miniplayer overlay / Picture-in-Picture / in-app draggable overlay architecture.
- Identify all relevant files, classes, dependencies, extension points, and potential pitfalls.
- Produce a detailed survey report at `c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\explorer_survey_1\survey_report.md` and deliver `handoff.md`.

## 2026-10-05T20:59:00Z
You are the Video Engine Explorer (survey round).
Your assigned working directory is: c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\explorer_survey_1
Your parent is the Project Orchestrator (orchestrator_1, conversation ID: 0a34afd4-fc8e-48e4-a3d6-ca63de92c85e).

Mandatory instructions:
1. Read c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\ORIGINAL_REQUEST.md.
2. Read your task assignment in c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\explorer_survey_1\DISPATCH.md.
3. Explore the codebase using find_by_name, grep_search, view_file. Note: you are read-only.
4. Focus specifically on R1 (YouTube & MX Player Grade Video Engine):
   - Loudness booster (200% gain via LoudnessEnhancer / audio processor)
   - External .srt / .vtt subtitles (auto-detect in directory and storage picker)
   - Persistent video resume memory in database and auto-resume prompt
   - Quality selection & black-screen prevention on single-rendition local offline files
   - In-app draggable/floating video miniplayer overlay
5. Write your comprehensive analysis report to c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\explorer_survey_1\survey_report.md.
6. Write handoff.md in your working directory following the Handoff Protocol.
7. Send a message to parent (0a34afd4-fc8e-48e4-a3d6-ca63de92c85e) with your completion status and path to survey_report.md.
