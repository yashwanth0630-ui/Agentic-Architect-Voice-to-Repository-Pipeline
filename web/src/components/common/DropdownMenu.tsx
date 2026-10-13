import React, { useState, useRef, useEffect } from 'react';
import { MoreVertical } from 'lucide-react';

export interface DropdownItem {
  id: string;
  label: string;
  icon?: React.ReactNode;
  isDestructive?: boolean;
  onClick: () => void;
}

interface DropdownMenuProps {
  items: DropdownItem[];
  triggerIcon?: React.ReactNode;
  align?: 'left' | 'right';
  className?: string;
}

export const DropdownMenu: React.FC<DropdownMenuProps> = ({
  items,
  triggerIcon,
  align = 'right',
  className = '',
}) => {
  const [isOpen, setIsOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
        setIsOpen(false);
      }
    };

    if (isOpen) {
      document.addEventListener('mousedown', handleClickOutside);
    }
    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
    };
  }, [isOpen]);

  return (
    <div className={`relative inline-block text-left ${className}`} ref={containerRef}>
      <button
        type="button"
        onClick={(e) => {
          e.stopPropagation();
          setIsOpen(!isOpen);
        }}
        className="p-1.5 text-[#a8a29e] hover:text-[#faf8f5] hover:bg-[#2c2927] rounded-lg transition-colors active:scale-95"
        aria-label="Open actions menu"
      >
        {triggerIcon || <MoreVertical className="w-4 h-4" />}
      </button>

      {isOpen && (
        <div
          className={`absolute z-50 mt-1 w-44 rounded-xl bg-[#211f1d] border border-[#292524] shadow-[0_8px_24px_rgba(0,0,0,0.65)] py-1.5 backdrop-blur-md animate-in fade-in zoom-in-95 duration-100 ${
            align === 'right' ? 'right-0' : 'left-0'
          }`}
          role="menu"
        >
          {items.map((item) => (
            <button
              key={item.id}
              role="menuitem"
              onClick={(e) => {
                e.stopPropagation();
                setIsOpen(false);
                item.onClick();
              }}
              className={`w-full text-left px-3 py-2 text-[12px] flex items-center gap-2.5 transition-colors ${
                item.isDestructive
                  ? 'text-[#ff6b00] hover:bg-[#291717]'
                  : 'text-[#e7e1de] hover:bg-[#2c2927] hover:text-[#faf8f5]'
              }`}
            >
              {item.icon && <span className="opacity-80">{item.icon}</span>}
              <span className="font-medium">{item.label}</span>
            </button>
          ))}
        </div>
      )}
    </div>
  );
};
