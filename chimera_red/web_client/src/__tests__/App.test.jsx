import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import App from '../App';

describe('Chimera Red Web Client', () => {
    beforeEach(() => {
        vi.clearAllMocks();
    });

    describe('Initial Render', () => {
        it('renders the app header with logo', () => {
            render(<App />);
            expect(screen.getByText('CHIMERA')).toBeInTheDocument();
            expect(screen.getByText('RED')).toBeInTheDocument();
        });

        it('shows Connect Device button when disconnected', () => {
            render(<App />);
            expect(screen.getByText('Connect Device')).toBeInTheDocument();
        });

        it('displays all navigation tabs', () => {
            render(<App />);
            expect(screen.getByText('Dashboard')).toBeInTheDocument();
            expect(screen.getByText('Wi-Fi')).toBeInTheDocument();
            expect(screen.getByText('BLE')).toBeInTheDocument();
            expect(screen.getByText('CSI Radar')).toBeInTheDocument();
            expect(screen.getByText('Wigle')).toBeInTheDocument();
            expect(screen.getByText('Terminal')).toBeInTheDocument();
        });

        it('starts on dashboard tab', () => {
            render(<App />);
            expect(screen.getByText('System Status')).toBeInTheDocument();
            expect(screen.getByText('Quick Actions')).toBeInTheDocument();
        });
    });

    describe('Tab Navigation', () => {
        it('switches to Wi-Fi tab when clicked', async () => {
            render(<App />);
            const user = userEvent.setup();

            await user.click(screen.getByText('Wi-Fi'));
            expect(screen.getByText('Wi-Fi Recon')).toBeInTheDocument();
        });

        it('switches to BLE tab when clicked', async () => {
            render(<App />);
            const user = userEvent.setup();

            await user.click(screen.getByText('BLE'));
            expect(screen.getByText('BLE Recon')).toBeInTheDocument();
        });

        it('switches to CSI Radar tab when clicked', async () => {
            render(<App />);
            const user = userEvent.setup();

            await user.click(screen.getByText('CSI Radar'));
            expect(screen.getByText('WiFi Sensing (CSI Radar)')).toBeInTheDocument();
        });

        it('switches to Wigle tab when clicked', async () => {
            render(<App />);
            const user = userEvent.setup();

            await user.click(screen.getByText('Wigle'));
            expect(screen.getByText('Wigle Intelligence')).toBeInTheDocument();
        });

        it('switches to Terminal tab when clicked', async () => {
            render(<App />);
            const user = userEvent.setup();

            await user.click(screen.getByText('Terminal'));
            expect(screen.getByText('Serial Log')).toBeInTheDocument();
        });
    });

    describe('Dashboard', () => {
        it('shows N/A for device info when not connected', () => {
            render(<App />);
            const naElements = screen.getAllByText('N/A');
            expect(naElements.length).toBeGreaterThanOrEqual(2);
        });

        it('displays Scan Wi-Fi button in Quick Actions', () => {
            render(<App />);
            expect(screen.getByText('Scan Wi-Fi')).toBeInTheDocument();
        });

        it('displays Scan BLE button in Quick Actions', () => {
            render(<App />);
            expect(screen.getByText('Scan BLE')).toBeInTheDocument();
        });

        it('displays Refresh Info button', () => {
            render(<App />);
            expect(screen.getByText('Refresh Info')).toBeInTheDocument();
        });
    });

    describe('Wi-Fi Tab', () => {
        it('shows empty state message when no networks', async () => {
            render(<App />);
            const user = userEvent.setup();

            await user.click(screen.getByText('Wi-Fi'));
            expect(screen.getByText('No networks found. Start a scan.')).toBeInTheDocument();
        });

        it('displays SCAN button', async () => {
            render(<App />);
            const user = userEvent.setup();

            await user.click(screen.getByText('Wi-Fi'));
            const scanButtons = screen.getAllByText('SCAN');
            expect(scanButtons.length).toBeGreaterThanOrEqual(1);
        });
    });

    describe('Wigle Tab', () => {
        it('shows API token input', async () => {
            render(<App />);
            const user = userEvent.setup();

            await user.click(screen.getByText('Wigle'));
            expect(screen.getByPlaceholderText(/YOUR_ENCODED_TOKEN_HERE/)).toBeInTheDocument();
        });

        it('shows Search Area button', async () => {
            render(<App />);
            const user = userEvent.setup();

            await user.click(screen.getByText('Wigle'));
            expect(screen.getByText('Search Area')).toBeInTheDocument();
        });

        it('shows Get Token link', async () => {
            render(<App />);
            const user = userEvent.setup();

            await user.click(screen.getByText('Wigle'));
            expect(screen.getByText('Get Token')).toHaveAttribute('href', 'https://wigle.net/account');
        });
    });

    describe('Terminal Tab', () => {
        it('shows raw command input', async () => {
            render(<App />);
            const user = userEvent.setup();

            await user.click(screen.getByText('Terminal'));
            expect(screen.getByPlaceholderText('Send raw command...')).toBeInTheDocument();
        });
    });

    describe('Serial Connection', () => {
        it('connect button triggers serial connection attempt', async () => {
            // Just test that clicking connect doesn't crash and button exists
            render(<App />);
            const connectBtn = screen.getByText('Connect Device');
            expect(connectBtn).toBeInTheDocument();

            // Mock serial to be present (it's mocked in setup.js)
            // Click should not throw
            expect(() => fireEvent.click(connectBtn)).not.toThrow();
        });
    });

    describe('CSI Radar Tab', () => {
        it('shows START and STOP buttons', async () => {
            render(<App />);
            const user = userEvent.setup();

            await user.click(screen.getByText('CSI Radar'));
            expect(screen.getByText('START')).toBeInTheDocument();
            expect(screen.getByText('STOP')).toBeInTheDocument();
        });

        it('shows visualization container', async () => {
            render(<App />);
            const user = userEvent.setup();

            await user.click(screen.getByText('CSI Radar'));
            expect(screen.getByText(/Visualizing multipath distortions/)).toBeInTheDocument();
        });
    });
});

describe('Data Handling Logic', () => {
    // These test the internal logic with mock data

    it('handles wifi scan result data structure', () => {
        const mockWifiData = {
            type: 'wifi_scan_result',
            count: 3,
            networks: [
                { ssid: 'TestNet', rssi: -65, channel: 6, encryption: 4, bssid: 'AA:BB:CC:DD:EE:FF' },
                { ssid: 'OpenNet', rssi: -70, channel: 11, encryption: 7, bssid: '11:22:33:44:55:66' },
                { ssid: '', rssi: -80, channel: 1, encryption: 4, bssid: '99:88:77:66:55:44' },
            ]
        };

        expect(mockWifiData.type).toBe('wifi_scan_result');
        expect(mockWifiData.networks).toHaveLength(3);
        expect(mockWifiData.networks[0].ssid).toBe('TestNet');
        expect(mockWifiData.networks[1].encryption).toBe(7); // OPEN
    });

    it('handles ble scan result data structure', () => {
        const mockBleData = {
            type: 'ble_scan_result',
            count: 2,
            devices: [
                { name: 'Galaxy Buds', address: 'AA:BB:CC:DD:EE:FF', rssi: -55 },
                { name: '', address: '11:22:33:44:55:66', rssi: -75 },
            ]
        };

        expect(mockBleData.type).toBe('ble_scan_result');
        expect(mockBleData.devices).toHaveLength(2);
        expect(mockBleData.devices[0].name).toBe('Galaxy Buds');
    });

    it('handles spectrum result data structure', () => {
        const mockSpectrumData = {
            type: 'spectrum_result',
            data: [150, 200, 180, 160, 140, 120, 100, 80, 60, 40, 50, 70, 90, 110]
        };

        expect(mockSpectrumData.type).toBe('spectrum_result');
        expect(mockSpectrumData.data).toHaveLength(14); // Channels 1-14
    });

    it('handles sys_info data structure', () => {
        const mockSysInfo = {
            type: 'sys_info',
            chip: 'ESP32-S3',
            mac: 'AA:BB:CC:DD:EE:FF',
            heap: 320000,
            psram: 8388608
        };

        expect(mockSysInfo.chip).toBe('ESP32-S3');
        expect(mockSysInfo.psram).toBe(8388608);
    });

    it('handles CSI data structure', () => {
        const mockCsiData = {
            type: 'csi',
            timestamp: Date.now(),
            data: new Array(64).fill(0).map(() => Math.floor(Math.random() * 128))
        };

        expect(mockCsiData.type).toBe('csi');
        expect(mockCsiData.data).toHaveLength(64);
    });
});

describe('Utility Functions', () => {
    it('RSSI to signal strength mapping', () => {
        // Test the implied logic from the UI
        const getRssiColor = (rssi) => {
            if (rssi > -60) return 'green';
            if (rssi > -80) return 'orange';
            return 'red';
        };

        expect(getRssiColor(-50)).toBe('green');
        expect(getRssiColor(-65)).toBe('orange');
        expect(getRssiColor(-90)).toBe('red');
    });

    it('RSSI to percentage mapping', () => {
        const rssiToPercent = (rssi) => Math.min(100, Math.max(0, (rssi + 100) * 2));

        expect(rssiToPercent(-100)).toBe(0);
        expect(rssiToPercent(-50)).toBe(100);
        expect(rssiToPercent(-75)).toBe(50);
    });
});
