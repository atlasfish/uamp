# MockServer Integration - Deployment Checklist

## Pre-Deployment Checklist

### MockServer Setup
- [ ] MockServer is deployed and running
- [ ] Server is accessible at `http://10.0.2.2:8000` (from Android emulator)
- [ ] All required endpoints are implemented:
  - [ ] `/api/recommend/daily`
  - [ ] `/api/recommend/guess`
  - [ ] `/api/recommend/popular`
  - [ ] `/api/playlists/treasured`
  - [ ] `/music_list.json`

### API Verification
Test each endpoint manually:

```bash
# From your development machine (if server is on localhost:8000)
curl http://localhost:8000/api/recommend/daily
curl http://localhost:8000/api/recommend/guess
curl http://localhost:8000/api/recommend/popular
curl http://localhost:8000/api/playlists/treasured
curl http://localhost:8000/music_list.json
```

- [ ] All endpoints return valid JSON
- [ ] Song objects include required fields: `id`, `title`, `artist`, `album`, `source`, `image`, `duration`, `tags`, `isList`, `likes`
- [ ] Relative paths start with `/` (e.g., `/music/song.mp3`)
- [ ] At least some media files are available at the specified paths

## Build and Deploy Checklist

### Build the App
- [ ] Clean build: `./gradlew clean`
- [ ] Build debug APK: `./gradlew assembleDebug`
- [ ] Check for build errors
- [ ] Verify no deprecated API warnings

### Install on Device/Emulator
- [ ] Start Android emulator or connect device
- [ ] Install app: `./gradlew installDebug`
- [ ] Grant necessary permissions (storage, network)

## Testing Checklist

### Basic Functionality
- [ ] App launches successfully
- [ ] No crashes on startup
- [ ] Network permission granted
- [ ] Service starts without errors

### Browse Testing
- [ ] Media browser opens
- [ ] Root categories visible:
  - [ ] 今日推荐 (Daily Recommend)
  - [ ] 猜你喜欢 (Guess Like)
  - [ ] 最近流行 (Popular)
  - [ ] 宝藏歌单 (Treasured Playlists)
  - [ ] Albums
- [ ] Click on each category loads songs
- [ ] Song metadata displays correctly (title, artist, album art)
- [ ] Albums category shows albums grouped correctly

### Playback Testing
- [ ] Select a song from any category
- [ ] Song starts playing
- [ ] Album art displays
- [ ] Play/Pause works
- [ ] Skip forward/backward works
- [ ] Progress bar updates
- [ ] Notification shows current song
- [ ] Lock screen controls work

### Search Testing

#### Basic Search
- [ ] Open search in media browser
- [ ] Type keyword (e.g., "music")
- [ ] Results appear
- [ ] Results are playable

#### Filtered Search (if your client supports it)
- [ ] Search with genre filter
- [ ] Search with artist filter
- [ ] Search with album filter
- [ ] Search with tag filter
- [ ] Search with minLikes filter
- [ ] Combined filters work

### Android Auto Testing (if available)
- [ ] Connect to Android Auto
- [ ] Browse categories in Auto interface
- [ ] Play songs from Auto
- [ ] Search works in Auto
- [ ] Voice commands work

### Cast Testing (if available)
- [ ] Cast button appears
- [ ] Connect to Cast device
- [ ] Song casts successfully
- [ ] Album art shows on Cast device
- [ ] Controls work during casting

## Logging and Debugging

### Enable Detailed Logging
```bash
# View all UAMP-related logs
adb logcat | grep -E "MusicService|ApiSource|MultiBrowseTree|JsonSource"

# View only errors
adb logcat *:E | grep -E "MusicService|ApiSource"

# View network requests
adb logcat | grep "ApiSource"
```

### Key Log Messages to Check
- [ ] "Loading from API: http://..." - Source is fetching
- [ ] "Successfully loaded N items from..." - Source loaded successfully
- [ ] "Built category ... with N items" - Category built successfully
- [ ] "Search completed. Query: '...', Total results: N" - Search working

### Common Error Messages

| Error | Possible Cause | Solution |
|-------|----------------|----------|
| "Error downloading JSON" | Network issue | Check server is running, verify URL |
| "Failed to load from..." | API returned invalid JSON | Verify API response format |
| "loading music source failed" | Source initialization error | Check logs for specific error |
| "No address associated with hostname" | DNS/Network issue | Verify server address, check emulator network |

## Performance Testing

### Memory
- [ ] Monitor memory usage during playback
- [ ] Check for memory leaks (use Android Profiler)
- [ ] Verify no OutOfMemory errors with large catalogs

### Network
- [ ] Monitor network requests (use Network Profiler)
- [ ] Check for unnecessary duplicate requests
- [ ] Verify requests are not blocking UI

### Responsiveness
- [ ] Browse operations complete quickly (<2 seconds)
- [ ] Search returns results quickly (<1 second)
- [ ] No ANR (Application Not Responding) errors
- [ ] UI remains responsive during loading

## Edge Cases

### Network Conditions
- [ ] Test with slow network (use network throttling)
- [ ] Test with server unavailable
- [ ] Test with intermittent connectivity
- [ ] Test with timeout scenarios

### Data Conditions
- [ ] Test with empty catalog
- [ ] Test with very large catalog (1000+ songs)
- [ ] Test with missing album art
- [ ] Test with special characters in metadata

### Concurrent Access
- [ ] Multiple clients browsing simultaneously
- [ ] Rapid category switching
- [ ] Quick succession of searches

## Regression Testing

### Existing Features
- [ ] Old browsing still works (if not removed)
- [ ] Favorites/Recent still work
- [ ] Playback queue management works
- [ ] Background playback works
- [ ] Notification controls work

### Integrations
- [ ] Android Auto integration unaffected
- [ ] Cast integration unaffected
- [ ] Assistant voice commands work
- [ ] Media button controls work

## Security Checklist

- [ ] No hardcoded credentials in code
- [ ] HTTPS used in production (not HTTP)
- [ ] No sensitive data in logs (production builds)
- [ ] Proper error handling (no stack traces exposed)

## Documentation Review

- [ ] README updated with new features
- [ ] API documentation accurate
- [ ] Code comments are clear
- [ ] Architecture diagrams updated

## Production Readiness

### Before Release
- [ ] All tests passing
- [ ] No critical bugs
- [ ] Performance acceptable
- [ ] Security review complete
- [ ] Documentation complete

### Release Configuration
- [ ] Change BaseURL to production server
- [ ] Enable ProGuard/R8 (if using)
- [ ] Remove debug logging
- [ ] Sign APK with release key
- [ ] Test release build thoroughly

## Post-Deployment

### Monitoring
- [ ] Set up crash reporting (e.g., Firebase Crashlytics)
- [ ] Monitor API response times
- [ ] Track usage metrics
- [ ] Monitor error rates

### User Feedback
- [ ] Collect and review user feedback
- [ ] Monitor app store reviews
- [ ] Track support tickets
- [ ] Analyze usage patterns

## Rollback Plan

If issues are discovered:
1. Identify the issue severity
2. Check if it's API-related or app-related
3. Roll back to previous version if critical
4. Fix and redeploy

### Rollback Checklist
- [ ] Previous version APK available
- [ ] Database migration handled (if applicable)
- [ ] Users notified of rollback
- [ ] Root cause identified
- [ ] Fix planned and scheduled

## Sign-off

| Role | Name | Date | Signature |
|------|------|------|-----------|
| Developer | | | |
| QA | | | |
| Product Owner | | | |
| DevOps | | | |

## Notes

Use this section for any additional notes, issues discovered, or special considerations:

---

**Last Updated:** 2026-02-03
**Version:** 1.0
**Status:** Ready for Testing
