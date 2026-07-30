import React, { useEffect, useState } from 'react';

interface ToastProps {
  message: string;
  onDone: () => void;
}

const Toast: React.FC<ToastProps> = ({ message, onDone }) => {
  const [visible, setVisible] = useState(true);

  useEffect(() => {
    const timer = setTimeout(() => {
      setVisible(false);
      setTimeout(onDone, 200); // wait for fade-out
    }, 2000);
    return () => clearTimeout(timer);
  }, [onDone]);

  return (
    <div className={`toast ${visible ? 'toast-visible' : 'toast-hidden'}`}>
      {message}
    </div>
  );
};

export default Toast;
