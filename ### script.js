document.querySelector('.launch-button').addEventListener('click', () => {
    const launcher = document.querySelector('.launcher');
    const loadingScreen = document.querySelector('.loading-screen');

    launcher.classList.add('hidden');
    loadingScreen.classList.remove('hidden');

    // Simulate a launch phase (e.g., fetching data, initializing)
    setTimeout(() => {
        // Once the launch is complete, you can hide the loading screen
        // and show the main application or another UI element.
        loadingScreen.classList.add('hidden');
        launcher.classList.remove('hidden');
    }, 3000); // Simulate a 3-second loading time
});

// Function to simulate a kill event
function simulateKill() {
    const killBanner = document.querySelector('.kill-banner');
    killBanner.classList.remove('hidden');

    // Hide the banner after 2 seconds
    setTimeout(() => {
        killBanner.classList.add('hidden');
    }, 2000);
}

// Simulate a kill for testing purposes
simulateKill();
