import '@testing-library/jest-dom';
import { vi } from 'vitest';

// Mock Web Serial API
const mockPort = {
    open: vi.fn().mockResolvedValue(undefined),
    close: vi.fn().mockResolvedValue(undefined),
    readable: {
        pipeTo: vi.fn().mockResolvedValue(undefined),
        getReader: vi.fn().mockReturnValue({
            read: vi.fn().mockResolvedValue({ done: true }),
            releaseLock: vi.fn(),
        }),
    },
    writable: {
        getWriter: vi.fn().mockReturnValue({
            write: vi.fn().mockResolvedValue(undefined),
            releaseLock: vi.fn(),
        }),
    },
};

if (!navigator.serial) {
    Object.defineProperty(navigator, 'serial', {
        value: {
            requestPort: vi.fn().mockResolvedValue(mockPort),
            getPorts: vi.fn().mockResolvedValue([]),
        },
        writable: true,
    });
}

// Mock Geolocation API
const mockGeolocation = {
    getCurrentPosition: vi.fn((success) =>
        success({
            coords: {
                latitude: 37.7749,
                longitude: -122.4194,
                accuracy: 10,
            },
        })
    ),
    watchPosition: vi.fn(() => 1),
    clearWatch: vi.fn(),
};

Object.defineProperty(navigator, 'geolocation', {
    value: mockGeolocation,
    writable: true,
});

// Mock localStorage
const localStorageMock = {
    getItem: vi.fn(),
    setItem: vi.fn(),
    removeItem: vi.fn(),
    clear: vi.fn(),
};
Object.defineProperty(window, 'localStorage', {
    value: localStorageMock,
});

// Mock TextEncoderStream/TextDecoderStream for serial
global.TextEncoderStream = class {
    constructor() {
        this.readable = {
            pipeTo: vi.fn().mockResolvedValue(undefined),
        };
        this.writable = {
            getWriter: vi.fn().mockReturnValue({
                write: vi.fn().mockResolvedValue(undefined),
                releaseLock: vi.fn(),
            }),
        };
    }
};

global.TextDecoderStream = class {
    constructor() {
        this.readable = {
            getReader: vi.fn().mockReturnValue({
                read: vi.fn().mockResolvedValue({ done: true }),
                releaseLock: vi.fn(),
            }),
        };
        this.writable = {};
    }
};

// Mock scrollIntoView for JSDOM
Element.prototype.scrollIntoView = vi.fn();

// Suppress console errors during tests (can be noisy)
// console.error = vi.fn();
