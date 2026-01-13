import React, { useRef, useEffect } from 'react';
import { Terminal } from 'lucide-react';

export function TerminalView({ logs, onSend }) {
    const logEndRef = useRef(null);

    useEffect(() => {
        logEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    }, [logs]);

    return (
        <div className="card glass" style={{ height: '100%' }}>
            <h2><Terminal size={20} /> Serial Log</h2>
            <div className="log-terminal">
                {logs.map((log, i) => (
                    <div key={i}>{log}</div>
                ))}
                <div ref={logEndRef} />
            </div>
            <div style={{ marginTop: '10px', display: 'flex', gap: '10px' }}>
                <input type="text"
                    style={{ flex: 1, padding: '10px', background: 'transparent', border: '1px solid #333', color: '#fff', borderRadius: '4px' }}
                    placeholder="Send raw command..."
                    onKeyDown={(e) => {
                        if (e.key === 'Enter') {
                            onSend(e.target.value);
                            e.target.value = '';
                        }
                    }}
                />
            </div>
        </div>
    );
}
